package com.amigoscode.idempotency.demo;

import com.amigoscode.idempotency.IdempotencyApplication;
import com.amigoscode.idempotency.key.IdempotencyCleanup;
import com.amigoscode.idempotency.processor.SlowCardProcessor;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Starts the real application, plays a client that sends the same payment more than once to
 * POST /api/payments over HTTP, and prints how often the customer was charged. Run it with ./demo
 * (or .\demo on Windows). ./demo --help lists the flags.
 */
public final class Demo {

    private static final long CUSTOMER_ID = 1;
    private static final long START_BALANCE = 10_000;
    private static final long AMOUNT = 2_500;
    private static final long OTHER_AMOUNT = 4_000;
    private static final long SMALL_AMOUNT = 1_000;

    private static final long CLIENT_TIMEOUT_MILLIS = 1_000;
    private static final long SLOW_FIRST_CALL_MILLIS = 1_500;
    private static final long BACKOFF_MILLIS = 1_000;

    private static final Pattern CODE = Pattern.compile("\"code\"\\s*:\\s*\"([A-Z_]+)\"");
    private static final Pattern PAYMENT_ID = Pattern.compile("\"paymentId\"\\s*:\\s*(\\d+)");
    private static final Pattern BALANCE_AFTER = Pattern.compile("\"balanceAfterCents\"\\s*:\\s*(\\d+)");
    private static final Pattern MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private enum Scenario {
        TIMEOUT("timeout"),
        DOUBLE_CLICK("double-click"),
        REPLAY("replay"),
        DIFFERENT_BODY("different-body"),
        EXPIRED("expired");

        private final String flag;

        Scenario(String flag) {
            this.flag = flag;
        }

        static String names() {
            List<String> names = new ArrayList<>();
            for (Scenario scenario : values()) {
                names.add(scenario.flag);
            }
            return String.join(", ", names);
        }
    }

    private record Options(Scenario scenario, int requests, boolean sendKey, long processorMillis, boolean sql) {
    }

    private record Answer(int status, String code, String paymentId, Long balanceAfter, boolean replay,
                          String message, String body) {

        boolean isServerError() {
            return status >= 500;
        }
    }

    private final Options options;
    private final ConfigurableApplicationContext context;
    private final JdbcTemplate jdbc;
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final String baseUrl;
    private final List<Answer> answers = new ArrayList<>();
    private long startedAt;

    private Demo(Options options, ConfigurableApplicationContext context) {
        this.options = options;
        this.context = context;
        this.jdbc = context.getBean(JdbcTemplate.class);
        this.baseUrl = "http://localhost:" + context.getEnvironment().getProperty("local.server.port");
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
                // A 500 is shown as one line in the timeline instead of a full stack trace.
                "--logging.level.org.apache.catalina.core.ContainerBase=OFF",
                "--logging.level.org.hibernate.orm.jdbc.error=OFF",
                "--spring.web.error.include-message=always",
                "--payments.processor-millis=" + options.processorMillis(),
                "--payments.processor-first-call-millis="
                        + (options.scenario() == Scenario.TIMEOUT ? SLOW_FIRST_CALL_MILLIS : -1)));
        if (options.sql()) {
            springArgs.add("--logging.level.org.hibernate.SQL=DEBUG");
            springArgs.add("--logging.pattern.console=            %d{HH:mm:ss.SSS} [%replace(%thread){'http-nio-auto-\\d+-',''}] %msg%n");
        }

        SpringApplication app = new SpringApplication(IdempotencyApplication.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        try (ConfigurableApplicationContext context = app.run(springArgs.toArray(String[]::new))) {
            new Demo(options, context).run();
        }
    }

    private void run() throws Exception {
        // Fresh state for every run, and one throwaway request so the first payment is not slowed by startup work.
        jdbc.execute("truncate table idempotency_key, payment restart identity");
        jdbc.update("update customer set balance_cents = ?", START_BALANCE);
        http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/warm-up")).build(), HttpResponse.BodyHandlers.discarding());

        String key = options.sendKey() ? UUID.randomUUID().toString() : null;

        System.out.println();
        System.out.println("Scenario: " + options.scenario().flag
                + " | Idempotency-Key: " + (key == null ? "none (--no-key)" : key)
                + " | card processor: " + options.processorMillis() + " ms");
        System.out.println(story());
        System.out.println();

        startedAt = System.nanoTime();
        long wanted;
        long shouldBeCharged;
        switch (options.scenario()) {
            case TIMEOUT -> {
                retryAfterTimeout(key);
                wanted = 1;
                shouldBeCharged = AMOUNT;
            }
            case DOUBLE_CLICK -> {
                sameRequestAtTheSameMoment(key);
                wanted = 1;
                shouldBeCharged = AMOUNT;
            }
            case REPLAY -> {
                retryAfterAnotherPayment(key);
                wanted = 2;
                shouldBeCharged = AMOUNT + SMALL_AMOUNT;
            }
            case DIFFERENT_BODY -> {
                sameKeyDifferentBody(key);
                wanted = 1;
                shouldBeCharged = AMOUNT;
            }
            case EXPIRED -> {
                retryAfterTheKeyExpired(key);
                wanted = 1;
                shouldBeCharged = AMOUNT;
            }
            default -> throw new IllegalStateException();
        }
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

        int paymentRows = jdbc.queryForObject("select count(*) from payment", Integer.class);
        int keyRows = jdbc.queryForObject("select count(*) from idempotency_key", Integer.class);
        long balanceAfter = jdbc.queryForObject("select balance_cents from customer where id = ?", Long.class, CUSTOMER_ID);
        int processorCalls = context.getBean(SlowCardProcessor.class).calls();
        long serverErrors = answers.stream().filter(Answer::isServerError).count();

        System.out.println();
        if (options.requests() > 10 && options.scenario() == Scenario.DOUBLE_CLICK) {
            System.out.println("Answers:                         " + countByStatus());
        }
        System.out.println("Payments Maria wanted to make:   " + wanted + "  (" + money(shouldBeCharged) + ")");
        System.out.println("Requests the client sent:        " + answers.size());
        System.out.println("Card processor was called:       " + times(processorCalls));
        System.out.println("Payment rows in the database:    " + paymentRows);
        System.out.println("Idempotency-key rows:            " + keyRows);
        System.out.println("Balance before:                  " + money(START_BALANCE));
        System.out.println("Balance after:                   " + money(balanceAfter)
                + (balanceAfter == START_BALANCE - shouldBeCharged ? "" : "  (it should be " + money(START_BALANCE - shouldBeCharged) + ")"));
        if (serverErrors > 0) {
            System.out.println("Server errors (500):             " + serverErrors);
        }
        System.out.println("Time taken:                      " + elapsedMillis + " ms");
        System.out.println();

        for (String line : verdict(paymentRows, processorCalls, serverErrors)) {
            System.out.println("RESULT: " + line);
        }
        System.out.println();
    }

    private String story() {
        String who = "Maria (customer " + CUSTOMER_ID + ") has " + money(START_BALANCE) + " and pays " + money(AMOUNT) + " once.\n";
        return switch (options.scenario()) {
            case TIMEOUT -> who + "The answer takes too long: the client gives up after "
                    + CLIENT_TIMEOUT_MILLIS + " ms and sends the same request again.";
            case DOUBLE_CLICK -> who + (options.requests() == 2
                    ? "She double-clicks \"Pay\": the same request is sent twice at the same moment."
                    : "The same request is sent " + options.requests() + " times at the same moment.");
            case REPLAY -> who + "Then she makes a second, different payment of " + money(SMALL_AMOUNT)
                    + ". Then the client retries the first payment.";
            case DIFFERENT_BODY -> who + "Then a buggy client sends a payment of " + money(OTHER_AMOUNT)
                    + " with the SAME key.";
            case EXPIRED -> who + "A day later the key has expired and been deleted, and the client retries.";
        };
    }

    // ---- the scenarios -------------------------------------------------------------------------

    private void retryAfterTimeout(String key) throws Exception {
        log("Client", "-> POST /api/payments  " + money(AMOUNT) + "  (attempt 1)");
        CompletableFuture<Answer> first = sendAsync(AMOUNT, key);
        try {
            Answer answer = first.get(CLIENT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            log("Client", "<- " + describe(answer));
        } catch (TimeoutException e) {
            log("Client", "!! no answer after " + CLIENT_TIMEOUT_MILLIS + " ms: TIMEOUT. Was Maria charged? The client cannot know");
            first.thenAccept(answer -> log("(lost)", "<- " + describe(answer) + "   <- the server did answer. Nobody was listening"));
        }
        Thread.sleep(BACKOFF_MILLIS);
        answers.add(first.get(60, TimeUnit.SECONDS)); // the retry is sent after attempt 1 has finished on the server
        log("Client", "-> POST /api/payments  " + money(AMOUNT) + "  (attempt 2, " + (key == null ? "no key" : "same key") + ")");
        Answer second = send(AMOUNT, key);
        log("Client", "<- " + describe(second));
    }

    private void sameRequestAtTheSameMoment(String key) throws Exception {
        boolean timeline = options.requests() <= 10;
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Answer>> futures = new ArrayList<>();
        try (ExecutorService clicks = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 1; i <= options.requests(); i++) {
                String who = "Click " + i;
                futures.add(clicks.submit(() -> {
                    go.await();
                    if (timeline) {
                        log(who, "-> POST /api/payments  " + money(AMOUNT));
                    }
                    Answer answer = exchange(AMOUNT, key);
                    if (timeline) {
                        log(who, "<- " + describe(answer));
                    }
                    return answer;
                }));
            }
            startedAt = System.nanoTime();
            go.countDown();
        }
        for (Future<Answer> future : futures) {
            answers.add(future.get());
        }
    }

    private void retryAfterAnotherPayment(String key) throws Exception {
        String otherKey = key == null ? null : UUID.randomUUID().toString();
        log("Client", "-> POST /api/payments  " + money(AMOUNT) + "  (payment A, key A)");
        log("Client", "<- " + describe(send(AMOUNT, key)));
        log("Client", "-> POST /api/payments  " + money(SMALL_AMOUNT) + "  (payment B, key B)");
        log("Client", "<- " + describe(send(SMALL_AMOUNT, otherKey)));
        log("Client", "-> POST /api/payments  " + money(AMOUNT) + "  (payment A again, key A)");
        log("Client", "<- " + describe(send(AMOUNT, key)));
    }

    private void sameKeyDifferentBody(String key) throws Exception {
        log("Client", "-> POST /api/payments  " + money(AMOUNT));
        log("Client", "<- " + describe(send(AMOUNT, key)));
        log("Client", "-> POST /api/payments  " + money(OTHER_AMOUNT) + "  (same key, different amount)");
        log("Client", "<- " + describe(send(OTHER_AMOUNT, key)));
    }

    private void retryAfterTheKeyExpired(String key) throws Exception {
        log("Client", "-> POST /api/payments  " + money(AMOUNT));
        log("Client", "<- " + describe(send(AMOUNT, key)));
        jdbc.update("update idempotency_key set expires_at = now() - interval '1 second'");
        int deleted = context.getBean(IdempotencyCleanup.class).deleteExpired();
        log("Server", ".. 24 hours pass. The cleanup deletes " + deleted + " expired key row" + (deleted == 1 ? "" : "s"));
        log("Client", "-> POST /api/payments  " + money(AMOUNT) + "  (retry, same key)");
        log("Client", "<- " + describe(send(AMOUNT, key)));
    }

    // ---- the verdict ---------------------------------------------------------------------------

    private List<String> verdict(int paymentRows, int processorCalls, long serverErrors) {
        List<String> lines = new ArrayList<>();
        int sent = answers.size();
        switch (options.scenario()) {
            case TIMEOUT, DOUBLE_CLICK -> {
                String summary = "1 payment sent " + count(sent) + ". Customer charged " + times(paymentRows) + ".";
                if (paymentRows > 1) {
                    lines.add("DOUBLE CHARGE. " + summary);
                } else if (paymentRows == 0) {
                    lines.add("NOT CHARGED. " + summary);
                } else if (processorCalls > 1) {
                    lines.add("CARD CHARGED " + count(processorCalls).toUpperCase(Locale.ROOT) + ". The database has 1 payment, "
                            + "but the card processor was called " + times(processorCalls) + ". A rollback does not undo that.");
                } else {
                    lines.add("OK. " + summary);
                }
            }
            case REPLAY -> {
                Answer original = answers.get(0);
                Answer retry = answers.get(2);
                if (paymentRows > 2) {
                    lines.add("DOUBLE CHARGE. Payment A was sent twice. Customer charged " + times(paymentRows - 1) + " for it.");
                } else if (original.status() != retry.status() || !String.valueOf(original.body()).equals(retry.body())) {
                    lines.add("DIFFERENT ANSWER. The original said " + shortAnswer(original)
                            + ". The retry said " + shortAnswer(retry) + ".");
                } else if (!retry.replay()) {
                    lines.add("SAME ANSWER, NO HEADER. The retry got the original answer, but without Idempotent-Replay: true.");
                } else {
                    lines.add("OK. The retry got the original answer, byte for byte (" + shortAnswer(retry)
                            + "), marked Idempotent-Replay: true.");
                }
            }
            case DIFFERENT_BODY -> {
                Answer second = answers.get(1);
                if (paymentRows > 1) {
                    lines.add("BOTH CHARGED. One key was used for two different payments (" + money(AMOUNT) + " and "
                            + money(OTHER_AMOUNT) + ") and both went through.");
                } else if (second.status() == 422) {
                    lines.add("OK. The reused key was rejected with 422. Only the first payment (" + money(AMOUNT) + ") was charged.");
                } else if (second.status() < 300) {
                    lines.add("WRONG ANSWER. The client asked to pay " + money(OTHER_AMOUNT) + " and was told " + second.status()
                            + ", with the result of the " + money(AMOUNT) + " payment. Nothing was charged for " + money(OTHER_AMOUNT) + ".");
                } else {
                    lines.add("UNEXPECTED. The second request got " + second.status() + ". It should be a 422.");
                }
            }
            case EXPIRED -> {
                if (paymentRows > 1) {
                    lines.add("CHARGED AGAIN. The key was gone, so the retry was a new payment. Customer charged "
                            + times(paymentRows) + ".");
                } else {
                    lines.add("OK. 1 payment sent " + count(sent) + ". Customer charged " + times(paymentRows) + ".");
                }
            }
            default -> throw new IllegalStateException();
        }
        if (serverErrors > 0) {
            lines.add("SERVER ERROR. " + serverErrors + " of " + sent + " requests got a 500.");
        }
        return lines;
    }

    // ---- HTTP ----------------------------------------------------------------------------------

    private Answer send(long amountCents, String key) throws Exception {
        Answer answer = exchange(amountCents, key);
        answers.add(answer);
        return answer;
    }

    private Answer exchange(long amountCents, String key) throws Exception {
        return toAnswer(http.send(request(amountCents, key), HttpResponse.BodyHandlers.ofString()));
    }

    private CompletableFuture<Answer> sendAsync(long amountCents, String key) {
        return http.sendAsync(request(amountCents, key), HttpResponse.BodyHandlers.ofString()).thenApply(Demo::toAnswer);
    }

    private HttpRequest request(long amountCents, String key) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/payments"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"customerId\":" + CUSTOMER_ID + ",\"amountCents\":" + amountCents + "}"));
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return request.build();
    }

    private static Answer toAnswer(HttpResponse<String> response) {
        String body = response.body();
        String balanceAfter = find(BALANCE_AFTER, body);
        return new Answer(response.statusCode(), find(CODE, body), find(PAYMENT_ID, body),
                balanceAfter == null ? null : Long.valueOf(balanceAfter),
                response.headers().firstValue("Idempotent-Replay").map("true"::equalsIgnoreCase).orElse(false),
                find(MESSAGE, body), body);
    }

    // ---- printing ------------------------------------------------------------------------------

    private synchronized void log(String who, String message) {
        long millis = (System.nanoTime() - startedAt) / 1_000_000;
        System.out.printf("  +%5d ms  %-7s  %s%n", millis, who, message);
    }

    private static String describe(Answer answer) {
        if (answer.paymentId() != null) {
            return shortAnswer(answer) + (answer.replay() ? "  [Idempotent-Replay: true]" : "");
        }
        if (answer.code() != null) {
            return answer.status() + " " + answer.code();
        }
        String message = answer.message() == null ? "" : answer.message().replace("\\\"", "\"").replace("\\n", " ");
        if (message.length() > 90) {
            message = message.substring(0, 90) + "...";
        }
        return answer.status() + (message.isEmpty() ? "" : " (" + message + ")");
    }

    private static String shortAnswer(Answer answer) {
        if (answer.paymentId() == null) {
            return String.valueOf(answer.status());
        }
        return answer.status() + " payment #" + answer.paymentId()
                + (answer.balanceAfter() == null ? "" : ", balance after " + money(answer.balanceAfter()));
    }

    private String countByStatus() {
        Map<Integer, Integer> counts = new TreeMap<>();
        for (Answer answer : answers) {
            counts.merge(answer.status(), 1, Integer::sum);
        }
        List<String> parts = new ArrayList<>();
        counts.forEach((status, count) -> parts.add(status + " x " + count));
        return String.join(", ", parts);
    }

    private static String money(long cents) {
        return String.format(Locale.ROOT, "%d.%02d", cents / 100, cents % 100);
    }

    /** "1 time", "2 times". */
    private static String times(long n) {
        return n + (n == 1 ? " time" : " times");
    }

    /** "once", "twice", "5 times". */
    private static String count(long n) {
        return n == 1 ? "once" : n == 2 ? "twice" : n + " times";
    }

    private static String find(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body == null ? "" : body);
        return matcher.find() ? matcher.group(1) : null;
    }

    // ---- flags ---------------------------------------------------------------------------------

    private static Options parse(String[] args) {
        Scenario scenario = Scenario.TIMEOUT;
        int requests = 2;
        boolean requestsGiven = false;
        boolean sendKey = true;
        long processorMillis = 100;
        boolean sql = false;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--help", "-h" -> {
                    return null;
                }
                case "--scenario" -> scenario = scenario(value(args, ++i, arg));
                case "--requests" -> {
                    requests = number(value(args, ++i, arg), arg, 2, 200);
                    requestsGiven = true;
                }
                case "--no-key" -> sendKey = false;
                case "--processor-ms" -> processorMillis = number(value(args, ++i, arg), arg, 0, 120_000);
                case "--pause" -> processorMillis = 10_000;
                case "--sql" -> sql = true;
                default -> throw new IllegalArgumentException("Unknown option: " + arg);
            }
        }
        if (requestsGiven && scenario != Scenario.DOUBLE_CLICK) {
            throw new IllegalArgumentException("--requests only works with --scenario double-click");
        }
        return new Options(scenario, requests, sendKey, processorMillis, sql);
    }

    private static Scenario scenario(String value) {
        for (Scenario scenario : Scenario.values()) {
            if (scenario.flag.equalsIgnoreCase(value.trim())) {
                return scenario;
            }
        }
        throw new IllegalArgumentException("Unknown scenario '" + value + "'. Use one of: " + Scenario.names());
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
        System.out.println("  --scenario <name>   what the client does (default: timeout)");
        System.out.println("       timeout          the client times out waiting for the answer, then retries");
        System.out.println("       double-click     the same request is sent twice at the same moment");
        System.out.println("       replay           pay A, pay B, retry A: does the retry get A's original answer?");
        System.out.println("       different-body   the same key is sent again with a different amount");
        System.out.println("       expired          the key expires and is deleted, then the client retries");
        System.out.println("  --requests <n>      with double-click: send n copies at the same moment (default: 2)");
        System.out.println("  --no-key            the client does not send an Idempotency-Key header");
        System.out.println("  --processor-ms <n>  how long the card processor takes (default: 100)");
        System.out.println("  --pause             same as --processor-ms 10000: gives you 10 seconds to run ./inspect");
        System.out.println("  --sql               print every SQL statement, with the thread that ran it");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  ./demo");
        System.out.println("  ./demo --scenario double-click --sql");
        System.out.println("  ./demo --scenario double-click --requests 10");
        System.out.println("  ./demo --scenario different-body");
    }
}
