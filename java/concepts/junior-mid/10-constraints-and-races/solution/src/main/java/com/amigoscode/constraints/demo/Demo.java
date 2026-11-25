package com.amigoscode.constraints.demo;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import com.amigoscode.constraints.ConstraintsApplication;
import com.amigoscode.constraints.settings.Strategy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Starts the real application, sends requests at it over HTTP at the same moment, and prints what
 * ended up in the database. Run it with ./demo (or .\demo on Windows). ./demo --help lists the flags.
 */
public final class Demo {

    private static final List<String> SCENARIOS = List.of("signup", "case", "subscription", "settings", "theme", "bad-data");
    private static final String[] SPELLINGS = {"Ana@Example.com", "ana@example.com", "ANA@EXAMPLE.COM", "aNa@eXaMpLe.CoM"};
    private static final long MISSING_USER = 999_999;

    private static final Pattern CODE = Pattern.compile("\"code\"\\s*:\\s*\"([A-Z_]+)\"");
    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
    private static final Pattern CREATED = Pattern.compile("\"created\"\\s*:\\s*(true|false)");
    private static final Pattern THEME = Pattern.compile("\"theme\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private record Options(String scenario, int requests, long workMillis, Strategy strategy, boolean sql) {
    }

    private record Call(String label, HttpRequest request) {
    }

    private record Outcome(int status, String body) {

        String code() {
            return find(CODE, body);
        }

        boolean serverError() {
            return status >= 500;
        }
    }

    private static HttpClient http;
    private static String baseUrl;

    public static void main(String[] args) throws Exception {
        Options options;
        try {
            options = parse(args);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            System.out.println();
            printHelp();
            return;
        }
        if (options == null) {
            printHelp();
            return;
        }
        if (!Db.isUp()) {
            return;
        }

        List<String> springArgs = new ArrayList<>(List.of(
                "--server.port=0",
                "--logging.level.root=WARN",
                // A 500 is shown as one line in the timeline instead of a full stack trace.
                "--logging.level.org.apache.catalina.core.ContainerBase=OFF",
                "--spring.web.error.include-message=always",
                "--app.work-millis=" + options.workMillis()));
        if (options.sql()) {
            springArgs.add("--logging.level.org.hibernate.SQL=DEBUG");
            springArgs.add("--logging.level.org.springframework.jdbc.core.JdbcTemplate=DEBUG");
            springArgs.add("--logging.pattern.console=            %d{HH:mm:ss.SSS} [%replace(%thread){'http-nio-auto-\\d+-',''}] %msg%n");
        } else {
            // Hibernate logs every refused statement as an ERROR, also when the application handles it.
            springArgs.add("--logging.level.org.hibernate.engine.jdbc.spi.SqlExceptionHelper=OFF");
            springArgs.add("--logging.level.org.hibernate.orm.jdbc.error=OFF");
        }

        SpringApplication app = new SpringApplication(ConstraintsApplication.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        try (ConfigurableApplicationContext context = app.run(springArgs.toArray(String[]::new));
             Connection db = Db.connect()) {
            if (options.sql()) {
                hideJdbcTemplateNoise();
            }
            baseUrl = "http://localhost:" + context.getEnvironment().getProperty("local.server.port");
            http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

            // Fresh state for every run, and one throwaway request so the first caller is not slowed by startup work.
            try (Statement statement = db.createStatement()) {
                statement.execute("truncate table subscriptions, user_settings, users restart identity cascade");
            }
            http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/warm-up")).build(), HttpResponse.BodyHandlers.discarding());

            System.out.println();
            switch (options.scenario()) {
                case "signup" -> signup(options, db, false);
                case "case" -> signup(options, db, true);
                case "subscription" -> subscription(options, db);
                case "settings" -> settings(options, db);
                case "theme" -> theme(options, db);
                default -> badData(db);
            }
            System.out.println();
        }
    }

    /**
     * JdbcTemplate logs two lines per statement: "Executing prepared SQL update" and then the SQL.
     * This drops the first of the two, so that --sql prints one line per statement.
     */
    private static void hideJdbcTemplateNoise() {
        LoggerContext logging = (LoggerContext) LoggerFactory.getILoggerFactory();
        logging.addTurboFilter(new TurboFilter() {
            @Override
            public FilterReply decide(Marker marker, ch.qos.logback.classic.Logger logger, Level level,
                                      String format, Object[] params, Throwable t) {
                boolean noise = format != null && logger.getName().endsWith("JdbcTemplate")
                        && (format.equals("Executing prepared SQL update") || format.equals("Executing prepared SQL query"));
                return noise ? FilterReply.DENY : FilterReply.NEUTRAL;
            }
        });
    }

    // ---- signup and case: N requests sign up the same email at the same moment ----

    private static void signup(Options options, Connection db, boolean mixCase) throws Exception {
        String email = "ana@example.com";
        System.out.println("Scenario: " + options.scenario() + " | requests: " + options.requests()
                + " | work between check and insert: " + options.workMillis() + " ms");
        System.out.println(mixCase
                ? "Every request signs up the same address at the same moment, each with different capital letters."
                : "Every request signs up " + email + " at the same moment.");
        System.out.println();

        List<Outcome> outcomes = race(options.requests(), i -> {
            String spelling = mixCase ? SPELLINGS[i % SPELLINGS.length] : email;
            return new Call("POST /api/signups  " + spelling,
                    post("/api/signups", "{\"email\":\"" + spelling + "\",\"displayName\":\"Ana\"}"));
        });

        System.out.println();
        summary("Accounts created (201):", count(outcomes, 201));
        summary("Email taken (409):", count(outcomes, 409));
        printServerErrors(outcomes);
        System.out.println();
        System.out.println("ROWS IN users");
        Db.printTable(db, "select id, email, display_name from users order by id");
        System.out.println();

        int rows = Db.count(db, "select count(*) from users where lower(email) = '" + email + "'");
        if (rows > 1) {
            System.out.println("RESULT: DUPLICATES. " + rows + " rows in users for " + (mixCase ? "the same address." : email + "."));
        } else if (serverErrors(outcomes) > 0) {
            System.out.println("RESULT: SERVER ERROR. No duplicates, but " + requests(serverErrors(outcomes))
                    + " got a 500 instead of a clear answer.");
        } else {
            System.out.println("RESULT: OK. 1 row for " + email + ": " + count(outcomes, 201) + " created, "
                    + count(outcomes, 409) + " told the email is taken.");
        }
    }

    // ---- subscription: N requests subscribe the same user at the same moment ----

    private static void subscription(Options options, Connection db) throws Exception {
        long userId = createUser(db, "sam@example.com");
        System.out.println("Scenario: subscription | requests: " + options.requests()
                + " | work between check and insert: " + options.workMillis() + " ms");
        System.out.println("The rule: a user has at most one ACTIVE subscription. Every request subscribes user "
                + userId + " at the same moment.");
        System.out.println();

        List<Outcome> outcomes = race(options.requests(), i -> new Call("POST /api/subscriptions",
                post("/api/subscriptions", "{\"userId\":" + userId + ",\"plan\":\"pro\"}")));
        int active = Db.count(db, "select count(*) from subscriptions where status = 'ACTIVE' and user_id = " + userId);

        System.out.println();
        summary("Subscribed (201):", count(outcomes, 201));
        summary("Already subscribed (409):", count(outcomes, 409));
        printServerErrors(outcomes);
        summary("ACTIVE rows for user " + userId + ":", active);
        System.out.println();

        System.out.println("Then, one after another: cancel what is active, and subscribe again.");
        System.out.println();
        List<Long> activeIds = new ArrayList<>();
        try (Statement statement = db.createStatement();
             ResultSet result = statement.executeQuery("select id from subscriptions where status = 'ACTIVE' order by id")) {
            while (result.next()) {
                activeIds.add(result.getLong(1));
            }
        }
        for (long id : activeIds) {
            one("POST /api/subscriptions/" + id + "/cancel", post("/api/subscriptions/" + id + "/cancel", ""));
        }
        Outcome again = one("POST /api/subscriptions", post("/api/subscriptions", "{\"userId\":" + userId + ",\"plan\":\"basic\"}"));

        System.out.println();
        System.out.println("ROWS IN subscriptions");
        Db.printTable(db, "select id, user_id, plan, status from subscriptions order by id");
        System.out.println();

        if (active > 1) {
            System.out.println("RESULT: DUPLICATES. User " + userId + " had " + active + " ACTIVE subscriptions and pays for each of them.");
        } else if (serverErrors(outcomes) > 0) {
            System.out.println("RESULT: SERVER ERROR. One ACTIVE subscription, but " + requests(serverErrors(outcomes))
                    + " got a 500 instead of a clear answer.");
        } else if (again.status() != 201) {
            System.out.println("RESULT: TOO STRICT. The user cancelled and could not subscribe again.");
        } else {
            System.out.println("RESULT: OK. Never more than one ACTIVE subscription, and cancelled rows may repeat.");
        }
    }

    // ---- settings: N requests ask for the same user's settings for the first time ----

    private static void settings(Options options, Connection db) throws Exception {
        long userId = createUser(db, "lee@example.com");
        String strategy = options.strategy().label();
        System.out.println("Scenario: settings | strategy: " + strategy + " | requests: " + options.requests()
                + " | work between check and insert: " + options.workMillis() + " ms");
        System.out.println("User " + userId + " has no settings row yet. Every request asks for the settings at the same moment,");
        System.out.println("then one more request asks again.");
        System.out.println();

        String path = "/api/users/" + userId + "/settings?strategy=" + strategy;
        List<Outcome> outcomes = new ArrayList<>(race(options.requests(), i -> new Call("PUT /api/users/" + userId + "/settings", put(path, ""))));
        System.out.println();
        outcomes.add(one("PUT /api/users/" + userId + "/settings   (again, later)", put(path, "")));
        settingsSummary(outcomes, db, userId);
    }

    // ---- theme: N requests save the theme of a user that has no settings row yet ----

    private static void theme(Options options, Connection db) throws Exception {
        long userId = createUser(db, "kim@example.com");
        System.out.println("Scenario: theme | requests: " + options.requests()
                + " | work between check and insert: " + options.workMillis() + " ms");
        System.out.println("User " + userId + " has no settings row yet. Every request saves theme=dark at the same moment,");
        System.out.println("then one more request saves theme=blue.");
        System.out.println();

        String path = "/api/users/" + userId + "/settings/theme";
        List<Outcome> outcomes = new ArrayList<>(race(options.requests(),
                i -> new Call("PUT " + path + "  dark", put(path, "{\"theme\":\"dark\"}"))));
        System.out.println();
        outcomes.add(one("PUT " + path + "  blue   (later)", put(path, "{\"theme\":\"blue\"}")));
        settingsSummary(outcomes, db, userId);
    }

    private static void settingsSummary(List<Outcome> outcomes, Connection db, long userId) throws SQLException {
        long created = outcomes.stream().filter(o -> "true".equals(find(CREATED, o.body()))).count();
        System.out.println();
        summary("Answered (200):", count(outcomes, 200));
        summary("  of which created the row:", created);
        printServerErrors(outcomes);
        System.out.println();
        System.out.println("ROWS IN user_settings");
        if (Db.printTable(db, "select user_id, theme from user_settings order by user_id") == 0) {
            System.out.println("  none");
        }
        System.out.println();
        int rows = Db.count(db, "select count(*) from user_settings where user_id = " + userId);
        if (serverErrors(outcomes) > 0) {
            System.out.println("RESULT: SERVER ERROR. " + requests(serverErrors(outcomes)) + " got a 500. The primary key kept the data clean ("
                    + rows + (rows == 1 ? " row" : " rows") + "), the application did not cope with the refusal.");
        } else if (created != 1) {
            System.out.println("RESULT: WRONG ANSWER. 1 row was created, but " + created + " requests said they created it.");
        } else {
            System.out.println("RESULT: OK. 1 row, every request was answered, exactly 1 was told it created the row.");
        }
    }

    // ---- bad-data: four requests, one after another, that the application should never have accepted ----

    private static void badData(Connection db) throws Exception {
        long first = createUser(db, "bo@example.com");
        long second = createUser(db, "cy@example.com");
        System.out.println("Scenario: bad-data");
        System.out.println("Four requests, one after another. The application does not validate any of them.");
        System.out.println();

        List<Outcome> outcomes = new ArrayList<>();
        outcomes.add(one("signup without an email             ", post("/api/signups", "{\"displayName\":\"No Email\"}")));
        outcomes.add(one("subscribe with seats = -3           ",
                post("/api/subscriptions", "{\"userId\":" + first + ",\"plan\":\"pro\",\"seats\":-3}")));
        outcomes.add(one("subscribe, coupon bigger than price ",
                post("/api/subscriptions", "{\"userId\":" + second + ",\"plan\":\"basic\",\"couponCents\":5000}")));
        outcomes.add(one("subscribe a user that does not exist",
                post("/api/subscriptions", "{\"userId\":" + MISSING_USER + ",\"plan\":\"pro\"}")));

        System.out.println();
        summary("Accepted (201):", count(outcomes, 201));
        printServerErrors(outcomes);
        System.out.println();
        System.out.println("BAD ROWS IN THE DATABASE");
        int bad = Db.printTable(db, Inspect.BAD_ROWS);
        if (bad == 0) {
            System.out.println("  none");
        }
        System.out.println();
        if (bad > 0) {
            System.out.println("RESULT: BAD DATA. " + bad + " of 4 bad requests are now rows in the database.");
        } else {
            System.out.println("RESULT: OK. The database refused all 4. Nothing bad was stored.");
        }
    }

    // ---- sending requests ----

    /** Sends {@code n} requests at the same moment and waits for all the answers. */
    private static List<Outcome> race(int n, IntFunction<Call> calls) throws Exception {
        boolean timeline = n <= 10;
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Outcome>> futures = new ArrayList<>();
        long[] startedAt = new long[1];
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < n; i++) {
                Call call = calls.apply(i);
                String who = "Request " + (i + 1);
                futures.add(executor.submit(() -> {
                    go.await();
                    if (timeline) {
                        log(startedAt[0], who + "  -> " + call.label());
                    }
                    Outcome outcome = send(call.request());
                    if (timeline) {
                        log(startedAt[0], who + "  <- " + describe(outcome));
                    }
                    return outcome;
                }));
            }
            startedAt[0] = System.nanoTime();
            go.countDown();
        }
        long elapsedMillis = (System.nanoTime() - startedAt[0]) / 1_000_000;
        List<Outcome> outcomes = new ArrayList<>();
        for (Future<Outcome> future : futures) {
            outcomes.add(future.get());
        }
        if (!timeline) {
            System.out.println("  " + n + " requests answered in " + elapsedMillis + " ms");
        }
        return outcomes;
    }

    /** Sends one request and prints it with its answer. */
    private static Outcome one(String label, HttpRequest request) throws Exception {
        Outcome outcome = send(request);
        System.out.println("  " + label + "  <- " + describe(outcome));
        return outcome;
    }

    private static Outcome send(HttpRequest request) throws Exception {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        return new Outcome(response.statusCode(), response.body());
    }

    /** Sets up a user with plain SQL, so that the scenario itself is the only thing in the output. */
    private static long createUser(Connection db, String email) throws SQLException {
        try (Statement statement = db.createStatement();
             ResultSet result = statement.executeQuery(
                     "insert into users (email, display_name) values ('" + email + "', 'Demo') returning id")) {
            result.next();
            return result.getLong(1);
        }
    }

    private static HttpRequest post(String path, String json) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
    }

    private static HttpRequest put(String path, String json) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();
    }

    // ---- printing ----

    private static String describe(Outcome outcome) {
        String body = outcome.body();
        if (outcome.code() != null) {
            return outcome.status() + " " + outcome.code();
        }
        if (outcome.status() == 201 || outcome.status() == 200) {
            String created = find(CREATED, body);
            if (created != null) {
                return outcome.status() + " theme=" + find(THEME, body) + " created=" + created;
            }
            if (body.contains("\"status\"")) {
                return outcome.status() + " subscription #" + find(ID, body) + (body.contains("CANCELLED") ? " cancelled" : " active");
            }
            return outcome.status() + " user #" + find(ID, body) + " created";
        }
        return outcome.status() + " " + reason(find(MESSAGE, body));
    }

    /** The reason for a 500, cut down to what the database itself said when there is such a part. */
    private static String reason(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String text = message.replace("\\n", " ").replace("\\\"", "\"").replaceAll("\\s+", " ");
        int fromDatabase = text.indexOf("ERROR: ");
        if (fromDatabase >= 0) {
            text = text.substring(fromDatabase);
            int end = text.indexOf("] [");
            text = end > 0 ? text.substring(0, end) : text;
            text = text.replaceAll(" Detail: Failing row contains.*", "");
        }
        return "(" + (text.length() > 200 ? text.substring(0, 200) + "..." : text) + ")";
    }

    private static void summary(String label, long value) {
        System.out.printf("%-28s %d%n", label, value);
    }

    private static void printServerErrors(List<Outcome> outcomes) {
        if (serverErrors(outcomes) > 0) {
            summary("Server errors (500):", serverErrors(outcomes));
        }
    }

    private static int count(List<Outcome> outcomes, int status) {
        return (int) outcomes.stream().filter(outcome -> outcome.status() == status).count();
    }

    private static int serverErrors(List<Outcome> outcomes) {
        return (int) outcomes.stream().filter(Outcome::serverError).count();
    }

    private static String requests(int n) {
        return n + (n == 1 ? " request" : " requests");
    }

    private static synchronized void log(long startedAtNanos, String message) {
        long millis = (System.nanoTime() - startedAtNanos) / 1_000_000;
        System.out.printf("  +%5d ms  %s%n", millis, message);
    }

    private static String find(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body == null ? "" : body);
        return matcher.find() ? matcher.group(1) : null;
    }

    // ---- options ----

    private static Options parse(String[] args) {
        String scenario = "signup";
        int requests = 2;
        long workMillis = 100;
        Strategy strategy = Strategy.ON_CONFLICT;
        boolean sql = false;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--help", "-h" -> {
                    return null;
                }
                case "--scenario" -> {
                    scenario = value(args, ++i, arg);
                    if (!SCENARIOS.contains(scenario)) {
                        throw new IllegalArgumentException("Unknown scenario '" + scenario + "'. Use one of: "
                                + String.join(", ", SCENARIOS));
                    }
                }
                case "--strategy" -> {
                    try {
                        strategy = Strategy.from(value(args, ++i, arg));
                    } catch (Strategy.UnknownStrategyException e) {
                        throw new IllegalArgumentException(e.getMessage());
                    }
                }
                case "--requests" -> requests = number(value(args, ++i, arg), arg, 1, 500);
                case "--work-ms" -> workMillis = number(value(args, ++i, arg), arg, 0, 120_000);
                case "--sql" -> sql = true;
                default -> throw new IllegalArgumentException("Unknown option: " + arg);
            }
        }
        return new Options(scenario, requests, workMillis, strategy, sql);
    }

    private static String value(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException(option + " needs a value");
        }
        return args[index];
    }

    private static int number(String value, String option, int min, int max) {
        try {
            int number = Integer.parseInt(value);
            if (number < min || number > max) {
                throw new NumberFormatException();
            }
            return number;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(option + " must be a number from " + min + " to " + max + ", got: " + value);
        }
    }

    private static void printHelp() {
        System.out.println("Usage: ./demo [options]        (Windows: .\\demo [options])");
        System.out.println();
        System.out.println("  --scenario <name>   what to try (default: signup)");
        System.out.println("                        signup        the same email signs up twice at the same moment");
        System.out.println("                        case          the same email, written with different capital letters");
        System.out.println("                        subscription  one user subscribes twice at the same moment");
        System.out.println("                        settings      get-or-create of a settings row (see --strategy)");
        System.out.println("                        theme         create-or-update of a settings row");
        System.out.println("                        bad-data      four requests the application should have refused");
        System.out.println("  --strategy <name>   for --scenario settings: " + Strategy.names() + " (default: on-conflict)");
        System.out.println("  --requests <n>      how many requests are sent at the same moment (default: 2)");
        System.out.println("  --work-ms <n>       time between the check and the insert (default: 100)");
        System.out.println("  --sql               print every SQL statement, with the thread that ran it");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  ./demo");
        System.out.println("  ./demo --sql");
        System.out.println("  ./demo --scenario subscription --requests 20");
        System.out.println("  ./demo --scenario settings --strategy catch-inside");
    }

    private Demo() {
    }
}
