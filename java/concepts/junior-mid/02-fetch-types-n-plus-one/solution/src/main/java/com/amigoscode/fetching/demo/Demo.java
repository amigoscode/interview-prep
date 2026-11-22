package com.amigoscode.fetching.demo;

import com.amigoscode.fetching.FetchingApplication;
import com.amigoscode.fetching.order.FetchPlan;
import com.amigoscode.fetching.seed.Seed;
import com.amigoscode.fetching.sql.SqlRecorder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Starts the real application, fills the shop, sends ONE request over HTTP and prints how many SQL
 * statements that one request ran. Run it with ./demo (or .\demo on Windows). ./demo --help lists
 * the flags.
 */
public final class Demo {

    private static final Pattern MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern EXCEPTION = Pattern.compile("\"exception\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern SELECT_LIST = Pattern.compile("^select (.*?) from ", Pattern.DOTALL);

    /** A request that runs more statements than this, for one list or one order, is reported as N+1. */
    private static final int FEW_STATEMENTS = 6;

    private enum Endpoint {
        ORDERS, REPORT, ORDER, PAGE;

        static Endpoint from(String value) {
            for (Endpoint endpoint : values()) {
                if (endpoint.name().equalsIgnoreCase(value)) {
                    return endpoint;
                }
            }
            throw new IllegalArgumentException("--endpoint must be one of: orders | report | order | page, got: " + value);
        }
    }

    private record Options(Endpoint endpoint, FetchPlan fetch, int orders, int customers, long orderId,
                           int page, int size, boolean sql, boolean openInView, int batchSize) {

        String path() {
            return switch (endpoint) {
                case ORDERS -> "/api/orders?fetch=" + fetch.label();
                case REPORT -> "/api/orders/report";
                case ORDER -> "/api/orders/" + orderId;
                case PAGE -> "/api/orders/page?page=" + page + "&size=" + size;
            };
        }
    }

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
                // A 500 is shown as one RESULT line instead of a full stack trace.
                "--logging.level.org.apache.catalina.core.ContainerBase=OFF",
                "--logging.pattern.console=[%level] %msg%n%n",
                "--spring.web.error.include-message=always",
                "--spring.web.error.include-exception=true"));
        if (options.openInView()) {
            springArgs.add("--spring.jpa.open-in-view=true");
        }
        if (options.batchSize() > 0) {
            springArgs.add("--spring.jpa.properties.hibernate.default_batch_fetch_size=" + options.batchSize());
        }

        SpringApplication app = new SpringApplication(FetchingApplication.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        try (ConfigurableApplicationContext context = app.run(springArgs.toArray(String[]::new))) {
            run(options, context);
        }
    }

    private static void run(Options options, ConfigurableApplicationContext context) throws Exception {
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        String baseUrl = "http://localhost:" + context.getEnvironment().getProperty("local.server.port");
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + options.path())).build();

        // The same shop for every run.
        context.getBean(Seed.class).reset(options.orders(), options.customers());
        int items = jdbc.queryForObject("select count(*) from order_item", Integer.class);

        // One throwaway request first, so the measured one is not slowed down by startup work.
        LoggingSystem logging = context.getBean(LoggingSystem.class);
        logging.setLogLevel("ROOT", LogLevel.OFF);
        http.send(request, HttpResponse.BodyHandlers.discarding());
        logging.setLogLevel("ROOT", LogLevel.WARN);

        System.out.println();
        System.out.println("GET " + options.path());
        System.out.println("Shop: " + options.orders() + " orders, " + options.customers() + " customers, "
                + items + " order items, " + Seed.PRODUCTS + " products."
                + " open-in-view: " + (options.openInView() ? "ON" : "off")
                + (options.batchSize() > 0 ? ". default_batch_fetch_size: " + options.batchSize() : "") + ".");
        System.out.println();

        SqlRecorder.reset();
        long startedAt = System.nanoTime();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        long millis = (System.nanoTime() - startedAt) / 1_000_000;
        List<String> statements = SqlRecorder.statements();
        int count = SqlRecorder.count();
        String body = response.body() == null ? "" : response.body();

        System.out.println("1 request, " + count + " SQL " + (count == 1 ? "statement" : "statements"));
        System.out.println();
        if (options.sql()) {
            for (int i = 0; i < statements.size(); i++) {
                System.out.printf("  %4d  %s%n", i + 1, statements.get(i));
            }
            System.out.println();
            System.out.println("Grouped by shape:");
            System.out.println();
        }
        Map<String, Integer> shapes = new LinkedHashMap<>();
        for (String statement : statements) {
            shapes.merge(shape(statement), 1, Integer::sum);
        }
        shapes.forEach((shape, times) -> System.out.printf("  %5d x  %s%n", times, shape));
        if (!options.sql()) {
            System.out.println();
            System.out.println("  (--sql prints every statement in full)");
        }

        int ordersInResponse = occurrences(body, "\"customerName\"");
        System.out.println();
        System.out.println("HTTP status:            " + response.statusCode());
        if (response.statusCode() == 200 && ordersInResponse > 0) {
            System.out.println("Orders in the response: " + ordersInResponse);
        }
        if (body.length() <= 120) {
            System.out.println("Response body:          " + body);
        }
        System.out.println("Response size:          " + size(body.getBytes(StandardCharsets.UTF_8).length));
        System.out.println("Response time:          " + millis + " ms");
        System.out.println();
        System.out.println("RESULT: " + result(options, response.statusCode(), body, statements, count));
        System.out.println();
    }

    private static String result(Options options, int status, String body, List<String> statements, int count) {
        if (status != 200) {
            String exception = find(EXCEPTION, body);
            String message = find(MESSAGE, body);
            String what = exception == null ? "" : exception.substring(exception.lastIndexOf('.') + 1) + ": ";
            return "FAILED. " + status + " " + what + (message == null ? "" : message.replace("\\\"", "\""));
        }
        if (options.endpoint() == Endpoint.PAGE && statements.stream().noneMatch(Demo::hasLimit)) {
            return "PAGED IN MEMORY. No statement has a limit. The database returned every order, "
                    + "and Hibernate cut out the " + options.size() + " you asked for in Java.";
        }
        if (options.batchSize() > 0 && count > 1) {
            return "BATCHED. " + count + " statements instead of one per row. The number still grows with the data, "
                    + "one more for every " + options.batchSize() + " rows.";
        }
        if (count <= FEW_STATEMENTS) {
            return "OK. 1 request, " + count + " SQL " + (count == 1 ? "statement" : "statements") + ".";
        }
        return "N+1. 1 request ran " + count + " SQL statements: 1 query for the list, then "
                + (count - 1) + " more, one row at a time.";
    }

    private static boolean hasLimit(String sql) {
        String lower = sql.toLowerCase(Locale.ROOT);
        return lower.contains(" fetch first ") || lower.contains(" limit ");
    }

    /** The statement with a long select list shortened, so statements of the same shape are easy to compare. */
    private static String shape(String sql) {
        Matcher matcher = SELECT_LIST.matcher(sql);
        if (!matcher.find() || !matcher.group(1).contains(",")) {
            return sql;
        }
        int columns = matcher.group(1).split(",").length;
        return "select (" + columns + " columns) from " + sql.substring(matcher.end());
    }

    private static int occurrences(String text, String word) {
        int count = 0;
        for (int at = text.indexOf(word); at >= 0; at = text.indexOf(word, at + word.length())) {
            count++;
        }
        return count;
    }

    private static String size(int bytes) {
        return bytes < 1024 ? bytes + " bytes" : String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
    }

    private static String find(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static Options parse(String[] args) {
        Endpoint endpoint = Endpoint.ORDERS;
        FetchPlan fetch = FetchPlan.NAIVE;
        int orders = Seed.DEFAULT_ORDERS;
        int customers = -1;
        long orderId = 1;
        int page = 0;
        int size = 20;
        boolean sql = false;
        boolean openInView = false;
        int batchSize = 0;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--help", "-h" -> {
                    return null;
                }
                case "--endpoint" -> endpoint = Endpoint.from(value(args, ++i, arg));
                case "--fetch" -> fetch = fetch(value(args, ++i, arg));
                case "--orders" -> orders = number(value(args, ++i, arg), arg, 1, 1_000_000);
                case "--customers" -> customers = number(value(args, ++i, arg), arg, 1, 1_000_000);
                case "--id" -> orderId = number(value(args, ++i, arg), arg, 1, 1_000_000);
                case "--page" -> page = number(value(args, ++i, arg), arg, 0, 1_000_000);
                case "--size" -> size = number(value(args, ++i, arg), arg, 1, 100);
                case "--batch-size" -> batchSize = number(value(args, ++i, arg), arg, 1, 10_000);
                case "--open-in-view" -> openInView = true;
                case "--sql" -> sql = true;
                default -> throw new IllegalArgumentException("Unknown option: " + arg);
            }
        }
        if (customers < 0 || customers > orders) {
            customers = orders;
        }
        return new Options(endpoint, fetch, orders, customers, orderId, page, size, sql, openInView, batchSize);
    }

    private static FetchPlan fetch(String value) {
        try {
            return FetchPlan.from(value);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("--fetch must be one of: " + FetchPlan.names() + ", got: " + value);
        }
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
        System.out.println("Sends ONE request to the application and prints the SQL statements it ran.");
        System.out.println();
        System.out.println("  --endpoint <name>   orders  GET /api/orders          the order list (default)");
        System.out.println("                      report  GET /api/orders/report   orders per status");
        System.out.println("                      order   GET /api/orders/{id}     one order with its items");
        System.out.println("                      page    GET /api/orders/page     one page of orders with their items");
        System.out.println("  --fetch <plan>      for the order list: " + FetchPlan.names() + " (default: naive)");
        System.out.println("  --orders <n>        orders in the shop (default: 200)");
        System.out.println("  --customers <n>     customers they are shared between (default: one customer per order)");
        System.out.println("  --id <n>            which order, for --endpoint order (default: 1)");
        System.out.println("  --page <n>          which page, for --endpoint page (default: 0)");
        System.out.println("  --size <n>          page size, for --endpoint page (default: 20)");
        System.out.println("  --sql               print every SQL statement in full, in the order it ran");
        System.out.println("  --open-in-view      start the application with spring.jpa.open-in-view=true");
        System.out.println("  --batch-size <n>    start it with hibernate.default_batch_fetch_size=<n>");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  ./demo");
        System.out.println("  ./demo --fetch join-fetch --sql");
        System.out.println("  ./demo --orders 2000 --customers 50");
        System.out.println("  ./demo --endpoint order --open-in-view");
    }

    private Demo() {
    }
}
