package com.amigoscode.locking.demo;

import com.amigoscode.locking.LockingApplication;
import com.amigoscode.locking.order.Strategy;
import jakarta.persistence.EntityManagerFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hibernate.SessionFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Starts the real application, sends concurrent buyers at POST /api/orders over HTTP, and prints
 * what happened to the stock. Run it with ./demo (or .\demo on Windows). ./demo --help lists the flags.
 */
public final class Demo {

    private static final long PRODUCT_ID = 1;
    private static final Pattern CODE = Pattern.compile("\"code\"\\s*:\\s*\"([A-Z_]+)\"");
    private static final Pattern ORDER_ID = Pattern.compile("\"orderId\"\\s*:\\s*(\\d+)");
    private static final Pattern MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]*)\"");

    private record Options(Strategy strategy, int buyers, int stock, long fraudCheckMillis, boolean sql) {
    }

    private record Outcome(int status, String code, String orderId, String message) {
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
                "--spring.web.error.include-message=always",
                "--shop.fraud-check-millis=" + options.fraudCheckMillis(),
                "--spring.jpa.properties.hibernate.generate_statistics=true"));
        if (options.sql()) {
            springArgs.add("--logging.level.org.hibernate.SQL=DEBUG");
            springArgs.add("--logging.pattern.console=            %d{HH:mm:ss.SSS} [%replace(%thread){'http-nio-auto-\\d+-',''}] %msg%n");
        }

        SpringApplication app = new SpringApplication(LockingApplication.class);
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

        // Fresh state for every run, and one throwaway request so the first buyer is not slowed by startup work.
        jdbc.execute("truncate table orders restart identity");
        jdbc.update("update product set stock = ?, version = 0 where id = ?", options.stock(), PRODUCT_ID);
        http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/warm-up")).build(), HttpResponse.BodyHandlers.discarding());

        String strategy = options.strategy().name().toLowerCase(Locale.ROOT);
        boolean timeline = options.buyers() <= 10;

        System.out.println();
        System.out.println("Strategy: " + strategy
                + " | buyers: " + options.buyers()
                + " | stock: " + options.stock()
                + " | fraud check: " + options.fraudCheckMillis() + " ms");
        System.out.println("Every buyer orders 1 unit of product " + PRODUCT_ID + " at the same moment.");
        System.out.println();

        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/orders?strategy=" + strategy))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"productId\":" + PRODUCT_ID + ",\"quantity\":1}"))
                .build();

        CountDownLatch go = new CountDownLatch(1);
        List<Future<Outcome>> futures = new ArrayList<>();
        long[] startedAt = new long[1];
        try (ExecutorService buyers = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 1; i <= options.buyers(); i++) {
                String buyer = "Buyer " + i;
                futures.add(buyers.submit(() -> {
                    go.await();
                    if (timeline) {
                        log(startedAt[0], buyer + "  -> POST /api/orders");
                    }
                    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                    Outcome outcome = new Outcome(response.statusCode(),
                            find(CODE, response.body()), find(ORDER_ID, response.body()),
                            find(MESSAGE, response.body()));
                    if (timeline) {
                        log(startedAt[0], buyer + "  <- " + describe(outcome));
                    }
                    return outcome;
                }));
            }
            startedAt[0] = System.nanoTime();
            go.countDown();
        }
        long elapsedMillis = (System.nanoTime() - startedAt[0]) / 1_000_000;

        int confirmed = 0;
        int soldOut = 0;
        int conflicts = 0;
        List<String> errors = new ArrayList<>();
        for (Future<Outcome> future : futures) {
            Outcome outcome = future.get();
            if (outcome.status() == 201) {
                confirmed++;
            } else if ("SOLD_OUT".equals(outcome.code())) {
                soldOut++;
            } else if ("ORDER_CONFLICT".equals(outcome.code())) {
                conflicts++;
            } else {
                errors.add(String.valueOf(outcome.status()));
            }
        }

        int stockAfter = jdbc.queryForObject("select stock from product where id = ?", Integer.class, PRODUCT_ID);
        long versionAfter = jdbc.queryForObject("select version from product where id = ?", Long.class, PRODUCT_ID);
        int unitsSold = jdbc.queryForObject("select coalesce(sum(quantity), 0) from orders", Integer.class);
        long versionConflicts = context.getBean(EntityManagerFactory.class)
                .unwrap(SessionFactory.class).getStatistics().getOptimisticFailureCount();

        System.out.println();
        System.out.println("Stock before:              " + options.stock());
        System.out.println("Orders confirmed (201):    " + confirmed);
        System.out.println("Sold out (409):            " + soldOut);
        if (conflicts > 0 || options.strategy() == Strategy.OPTIMISTIC) {
            System.out.println("Gave up after retries (409): " + conflicts);
            System.out.println("Version conflicts detected: " + versionConflicts);
        }
        if (!errors.isEmpty()) {
            System.out.println("Server errors:             " + errors.size() + " " + errors.stream().distinct().toList());
        }
        System.out.println("Order rows in the database: " + unitsSold);
        System.out.println("Stock after:               " + stockAfter);
        System.out.println("Product version:           " + versionAfter);
        System.out.println("Time taken:                " + elapsedMillis + " ms");
        System.out.println();

        boolean oversold = unitsSold > options.stock();
        boolean lostUpdate = stockAfter != options.stock() - unitsSold;
        if (oversold) {
            System.out.println("RESULT: OVERSOLD. " + unitsSold + " orders were confirmed but only "
                    + options.stock() + " " + (options.stock() == 1 ? "item was" : "items were") + " in stock.");
        }
        if (lostUpdate) {
            System.out.println("RESULT: LOST UPDATE. " + unitsSold + " units were sold, so stock should be "
                    + (options.stock() - unitsSold) + ", but the database says " + stockAfter + ".");
        }
        if (!oversold && !lostUpdate) {
            System.out.println("RESULT: OK. Stock and orders agree: " + options.stock() + " - " + unitsSold
                    + " = " + stockAfter + ".");
        }
        System.out.println();
    }

    private static synchronized void log(long startedAtNanos, String message) {
        long millis = (System.nanoTime() - startedAtNanos) / 1_000_000;
        System.out.printf("  +%5d ms  %s%n", millis, message);
    }

    private static String describe(Outcome outcome) {
        if (outcome.status() == 201) {
            return "201 order #" + outcome.orderId() + " confirmed";
        }
        if (outcome.code() != null) {
            return outcome.status() + " " + outcome.code();
        }
        String message = outcome.message() == null ? "" : outcome.message();
        if (message.length() > 90) {
            message = message.substring(0, 90) + "...";
        }
        return outcome.status() + (message.isEmpty() ? "" : " (" + message + ")");
    }

    private static String find(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body == null ? "" : body);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static Options parse(String[] args) {
        Strategy strategy = Strategy.NAIVE;
        int buyers = 2;
        int stock = 1;
        long fraudCheckMillis = 100;
        boolean sql = false;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--help", "-h" -> {
                    return null;
                }
                case "--strategy" -> strategy = strategy(value(args, ++i, arg));
                case "--buyers" -> buyers = number(value(args, ++i, arg), arg, 1, 500);
                case "--stock" -> stock = number(value(args, ++i, arg), arg, 0, 1_000_000);
                case "--fraud-check-ms" -> fraudCheckMillis = number(value(args, ++i, arg), arg, 0, 120_000);
                case "--pause" -> fraudCheckMillis = 10_000;
                case "--sql" -> sql = true;
                default -> throw new IllegalArgumentException("Unknown option: " + arg);
            }
        }
        return new Options(strategy, buyers, stock, fraudCheckMillis, sql);
    }

    private static Strategy strategy(String value) {
        try {
            return Strategy.from(value);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(e.getMessage());
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
        System.out.println("  --strategy <name>     " + Strategy.names() + " (default: naive)");
        System.out.println("  --buyers <n>          how many buyers order at the same moment (default: 2)");
        System.out.println("  --stock <n>           units in stock before they start (default: 1)");
        System.out.println("  --fraud-check-ms <n>  time between reading the stock and writing it (default: 100)");
        System.out.println("  --pause               same as --fraud-check-ms 10000: gives you 10 seconds to run ./inspect");
        System.out.println("  --sql                 print every SQL statement, with the thread that ran it");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  ./demo");
        System.out.println("  ./demo --strategy pessimistic --sql");
        System.out.println("  ./demo --strategy optimistic --buyers 50 --stock 10");
    }

    private Demo() {
    }
}
