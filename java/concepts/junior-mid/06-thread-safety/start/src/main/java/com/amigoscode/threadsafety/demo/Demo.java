package com.amigoscode.threadsafety.demo;

import static com.amigoscode.threadsafety.demo.Tool.get;
import static com.amigoscode.threadsafety.demo.Tool.json;
import static com.amigoscode.threadsafety.demo.Tool.line;
import static com.amigoscode.threadsafety.demo.Tool.n;
import static com.amigoscode.threadsafety.demo.Tool.post;

import com.amigoscode.threadsafety.demo.Tool.Reply;
import com.amigoscode.threadsafety.demo.Tool.Run;
import com.amigoscode.threadsafety.pages.PageViewStats;
import com.amigoscode.threadsafety.pricing.SupplierPriceSource;
import com.amigoscode.threadsafety.sales.SalesStats;
import com.amigoscode.threadsafety.sales.SalesSummary;
import com.amigoscode.threadsafety.sales.WrongLockSalesStats;
import com.amigoscode.threadsafety.visits.VisitCounter;
import com.amigoscode.threadsafety.visits.VolatileVisitCounter;
import java.net.http.HttpClient;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Starts the real application, sends many HTTP requests at it at the same moment, and prints what
 * the beans ended up with. Run it with ./demo (or .\demo on Windows). ./demo --help lists the flags.
 *
 * Every run starts a fresh application, and all the state of this project lives in memory, so
 * every run starts from a clean state.
 */
public final class Demo {

    private static final long SALE_CENTS = 500;
    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private record Options(Scenario scenario, int requests, int atOnce, int products) {
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
        try (ConfigurableApplicationContext context = Tool.startApplication()) {
            HttpClient http = Tool.httpClient();
            String baseUrl = Tool.baseUrl(context);
            Tool.warmUp(http, baseUrl);
            System.out.println();
            System.out.println("Scenario: " + options.scenario().flag()
                    + " | requests: " + n(options.requests())
                    + " | in flight at the same time: " + options.atOnce());
            switch (options.scenario()) {
                case COUNTER -> counter(options, context, http, baseUrl, false);
                case VOLATILE -> counter(options, context, http, baseUrl, true);
                case PAGES -> pages(options, context, http, baseUrl);
                case HOT_PAGE -> hotPage(options, context, http, baseUrl);
                case PRICE_CACHE -> priceCache(options, context, http, baseUrl, false);
                case LOCKED_CACHE -> priceCache(options, context, http, baseUrl, true);
                case RECEIPT -> receipt(options, http, baseUrl);
                case LABELS -> labels(options, http, baseUrl);
                case SALES -> sales(options, context, http, baseUrl, false);
                case WRONG_LOCK -> sales(options, context, http, baseUrl, true);
            }
            System.out.println();
        }
        // A request thread that is stuck inside a corrupted HashMap must not keep the tool alive.
        System.exit(0);
    }

    // ---- counter / volatile -------------------------------------------------------------------

    private static void counter(Options options, ConfigurableApplicationContext context, HttpClient http,
                                String baseUrl, boolean isVolatile) throws Exception {
        String path = isVolatile ? "/api/visits/volatile" : "/api/visits";
        String bean = isVolatile ? "VolatileVisitCounter" : "VisitCounter";
        System.out.println("Every request is POST " + path + ". Each one calls " + bean + ".hit() once: one more visit.");
        System.out.println();

        Run run = Tool.send(http, options.requests(), options.atOnce(), i -> post(baseUrl + path));

        int counted = isVolatile
                ? context.getBean(VolatileVisitCounter.class).count()
                : context.getBean(VisitCounter.class).count();
        int answered = run.count(200);
        line("Requests sent:", n(options.requests()));
        line("Answered 200 OK:", n(answered));
        printFailures(run);
        line("Counter says:", n(counted));
        line("Lost:", n(answered - counted));
        line("Time taken:", n(run.millis()) + " ms");
        System.out.println();
        if (counted == answered) {
            System.out.println("RESULT: OK. " + n(answered) + " requests, and the counter says " + n(counted) + ".");
        } else {
            System.out.println("RESULT: LOST UPDATES. " + n(answered) + " requests were answered 200 OK, but the counter says "
                    + n(counted) + ". " + n(answered - counted) + " " + (answered - counted == 1 ? "visit is" : "visits are")
                    + " missing, and nothing failed.");
        }
    }

    // ---- pages: every request a different page ------------------------------------------------

    private static void pages(Options options, ConfigurableApplicationContext context, HttpClient http,
                              String baseUrl) throws Exception {
        System.out.println("Request 1 views page-1, request 2 views page-2, and so on: POST /api/pages/page-N/views.");
        System.out.println("Every page is viewed exactly once, so the map should end up with one entry per request.");
        System.out.println();

        Run run = Tool.send(http, options.requests(), options.atOnce(),
                i -> post(baseUrl + "/api/pages/page-" + (i + 1) + "/views"));

        PageViewStats stats = context.getBean(PageViewStats.class);
        int answered = run.count(200);
        int missing = 0;
        String example = null;
        for (Reply reply : run.replies()) {
            if (reply.status() == 200 && stats.views("page-" + (reply.index() + 1)) == 0) {
                missing++;
                if (example == null) {
                    example = "page-" + (reply.index() + 1);
                }
            }
        }
        line("Requests sent:", n(options.requests()));
        line("Answered 200 OK:", n(answered));
        printFailures(run);
        line("Pages the map says it holds:", n(stats.pages()) + "   (size())");
        line("Pages you can actually look up:", n(answered - missing));
        line("Pages that vanished:", n(missing) + (example == null ? "" : "   (for example " + example + ")"));
        line("Time taken:", n(run.millis()) + " ms");
        System.out.println();
        boolean ok = missing == 0 && stats.pages() == answered && answered == options.requests();
        if (ok) {
            System.out.println("RESULT: OK. " + n(answered) + " pages viewed, " + n(stats.pages()) + " pages in the map.");
        } else {
            if (missing > 0) {
                System.out.println("RESULT: LOST ENTRIES. " + n(missing) + " of " + n(answered)
                        + " pages were answered 200 OK and are not in the map.");
            }
            if (stats.pages() != answered - missing) {
                System.out.println("RESULT: CORRUPTED MAP. size() says " + n(stats.pages()) + ", but "
                        + n(answered - missing) + " pages can be looked up.");
            }
            int stuck = run.count(-1);
            if (stuck > 0) {
                System.out.println("RESULT: STUCK THREADS. " + n(stuck) + " requests never got an answer. Their threads are"
                        + " going round in circles inside the corrupted HashMap, using a full CPU core each.");
            }
            if (answered + stuck != options.requests()) {
                System.out.println("RESULT: ERRORS. " + n(options.requests() - answered - stuck) + " requests failed.");
            }
        }
    }

    // ---- hot-page: every request the same page ------------------------------------------------

    private static void hotPage(Options options, ConfigurableApplicationContext context, HttpClient http,
                                String baseUrl) throws Exception {
        System.out.println("Every request views the same page: POST /api/pages/home/views.");
        System.out.println();

        Run run = Tool.send(http, options.requests(), options.atOnce(), i -> post(baseUrl + "/api/pages/home/views"));

        int views = context.getBean(PageViewStats.class).views("home");
        int answered = run.count(200);
        line("Requests sent:", n(options.requests()));
        line("Answered 200 OK:", n(answered));
        printFailures(run);
        line("Views of 'home':", n(views));
        line("Lost:", n(answered - views));
        line("Time taken:", n(run.millis()) + " ms");
        System.out.println();
        if (views == answered) {
            System.out.println("RESULT: OK. " + n(answered) + " requests, " + n(views) + " views.");
        } else {
            System.out.println("RESULT: LOST UPDATES. " + n(answered) + " requests viewed 'home', but the map says "
                    + n(views) + ". " + n(answered - views) + " views are missing.");
        }
    }

    // ---- price-cache --------------------------------------------------------------------------

    private static void priceCache(Options options, ConfigurableApplicationContext context, HttpClient http,
                                   String baseUrl, boolean locked) throws Exception {
        String path = locked ? "/api/prices/locked/" : "/api/prices/";
        int products = Math.min(options.products(), options.requests());
        if (products == 1) {
            System.out.println("Every request is GET " + path + "ps5. The price is cached, so the slow supplier");
            System.out.println("(50 ms per call) should be asked exactly once.");
        } else {
            System.out.println("The requests ask for " + products + " different products: GET " + path + "product-N.");
            System.out.println("Prices are cached, so the slow supplier (50 ms per call) should be asked once per product.");
        }
        System.out.println();

        Run run = Tool.send(http, options.requests(), options.atOnce(),
                i -> get(baseUrl + path + (products == 1 ? "ps5" : "product-" + (i % products + 1))));

        int calls = context.getBean(SupplierPriceSource.class).calls();
        line("Requests sent:", n(options.requests()));
        line("Answered 200 OK:", n(run.count(200)));
        printFailures(run);
        line("Calls to the supplier:", n(calls));
        line("Calls needed:", n(products));
        line("Time taken:", n(run.millis()) + " ms");
        System.out.println();
        if (calls == products) {
            System.out.println("RESULT: OK. " + n(options.requests()) + " requests, " + n(calls)
                    + (calls == 1 ? " call" : " calls") + " to the supplier, " + n(run.millis()) + " ms.");
        } else {
            System.out.println("RESULT: DUPLICATE WORK. " + n(options.requests()) + " requests for "
                    + (products == 1 ? "the same price" : products + " prices") + " made " + n(calls)
                    + " calls to the supplier. " + n(calls - products) + " of them were wasted.");
        }
    }

    // ---- receipt ------------------------------------------------------------------------------

    private static void receipt(Options options, HttpClient http, String baseUrl) throws Exception {
        System.out.println("Customer N asks for their own receipt: GET /api/receipts?customer=customer-N&amountCents=...");
        System.out.println("Every customer pays a different amount.");
        System.out.println();

        Run run = Tool.send(http, options.requests(), options.atOnce(),
                i -> get(baseUrl + "/api/receipts?customer=customer-" + (i + 1) + "&amountCents=" + (1000 + i + 1)));

        int wrong = 0;
        List<String> examples = new ArrayList<>();
        for (Reply reply : run.replies()) {
            if (reply.status() != 200) {
                continue;
            }
            String asked = "customer-" + (reply.index() + 1);
            String got = json(reply.body(), "customer");
            String amount = json(reply.body(), "amountCents");
            if (!asked.equals(got) || !String.valueOf(1000 + reply.index() + 1).equals(amount)) {
                wrong++;
                if (examples.size() < 3) {
                    examples.add(asked + " paid " + (1000 + reply.index() + 1) + " and got the receipt of " + got
                            + " for " + amount);
                }
            }
        }
        line("Requests sent:", n(options.requests()));
        line("Answered 200 OK:", n(run.count(200)));
        printFailures(run);
        line("Got their own receipt:", n(run.count(200) - wrong));
        line("Got someone else's receipt:", n(wrong));
        examples.forEach(example -> System.out.println("    " + example));
        line("Time taken:", n(run.millis()) + " ms");
        System.out.println();
        if (wrong == 0) {
            System.out.println("RESULT: OK. Every customer got their own receipt.");
        } else {
            System.out.println("RESULT: DATA LEAK. " + n(wrong) + " of " + n(run.count(200))
                    + " customers were shown another customer's receipt. Every answer was 200 OK.");
        }
    }

    // ---- labels -------------------------------------------------------------------------------

    private static void labels(Options options, HttpClient http, String baseUrl) throws Exception {
        System.out.println("Request N asks for a shipping label: GET /api/labels?shipDate=<a date>.");
        System.out.println("The dates differ from request to request. The label must show the date that was asked for.");
        System.out.println();

        LocalDate first = LocalDate.of(2026, 1, 1);
        Run run = Tool.send(http, options.requests(), options.atOnce(),
                i -> get(baseUrl + "/api/labels?shipDate=" + first.plusDays(i % 3650)));

        int wrong = 0;
        List<String> examples = new ArrayList<>();
        for (Reply reply : run.replies()) {
            if (reply.status() != 200) {
                continue;
            }
            LocalDate asked = first.plusDays(reply.index() % 3650);
            String expected = "Ships on " + LABEL.format(asked);
            String got = json(reply.body(), "label");
            if (!expected.equals(got)) {
                wrong++;
                if (examples.size() < 3) {
                    examples.add("asked for " + asked + ", label says \"" + got + "\"");
                }
            }
        }
        line("Requests sent:", n(options.requests()));
        line("Answered 200 OK:", n(run.count(200)));
        printFailures(run);
        line("Labels with the right date:", n(run.count(200) - wrong));
        line("Labels with the WRONG date:", n(wrong));
        examples.forEach(example -> System.out.println("    " + example));
        line("Time taken:", n(run.millis()) + " ms");
        System.out.println();
        int failed = options.requests() - run.count(200);
        if (wrong == 0 && failed == 0) {
            System.out.println("RESULT: OK. Every label shows the date that was asked for.");
        } else {
            if (wrong > 0) {
                System.out.println("RESULT: WRONG DATES. " + n(wrong) + " of " + n(run.count(200))
                        + " labels were answered 200 OK with a date nobody asked for.");
            }
            if (failed > 0) {
                System.out.println("RESULT: ERRORS. " + n(failed) + " requests failed.");
            }
        }
    }

    // ---- sales / wrong-lock -------------------------------------------------------------------

    private static void sales(Options options, ConfigurableApplicationContext context, HttpClient http,
                              String baseUrl, boolean wrongLock) throws Exception {
        String path = wrongLock ? "/api/sales/wrong-lock" : "/api/sales";
        System.out.println("Half of the requests record a sale of 5.00: POST " + path + "?amountCents=500.");
        System.out.println("The other half read the dashboard: GET " + path + "/summary.");
        System.out.println("Every sale is 5.00, so in every summary revenue must be exactly orders x 5.00.");
        System.out.println();

        Run run = Tool.send(http, options.requests(), options.atOnce(), i -> i % 2 == 0
                ? post(baseUrl + path + "?amountCents=" + SALE_CENTS)
                : get(baseUrl + path + "/summary"));

        int sales = 0;
        int summaries = 0;
        int inconsistent = 0;
        List<String> examples = new ArrayList<>();
        for (Reply reply : run.replies()) {
            if (reply.status() != 200) {
                continue;
            }
            if (reply.index() % 2 == 0) {
                sales++;
                continue;
            }
            summaries++;
            long orders = Long.parseLong(json(reply.body(), "orders"));
            long revenue = Long.parseLong(json(reply.body(), "revenueCents"));
            if (revenue != orders * SALE_CENTS) {
                inconsistent++;
                if (examples.size() < 3) {
                    examples.add("orders=" + n(orders) + " revenueCents=" + n(revenue) + "   (" + n(orders) + " x 500 = "
                            + n(orders * SALE_CENTS) + ")");
                }
            }
        }
        SalesSummary total = wrongLock
                ? context.getBean(WrongLockSalesStats.class).summary()
                : context.getBean(SalesStats.class).summary();
        line("Requests sent:", n(options.requests()));
        printFailures(run);
        line("Sales recorded (200 OK):", n(sales));
        line("Summaries read:", n(summaries));
        line("Summaries that do not add up:", n(inconsistent));
        examples.forEach(example -> System.out.println("    " + example));
        line("Final orders:", n(total.orders()));
        line("Final revenue (cents):", n(total.revenueCents()));
        line("Time taken:", n(run.millis()) + " ms");
        System.out.println();
        boolean totalsRight = total.orders() == sales && total.revenueCents() == sales * SALE_CENTS;
        if (inconsistent == 0 && totalsRight) {
            System.out.println("RESULT: OK. Every summary added up, and the final totals match the " + n(sales) + " sales.");
        } else {
            if (inconsistent > 0) {
                System.out.println("RESULT: TORN READ. " + n(inconsistent) + " of " + n(summaries)
                        + " summaries showed orders and revenue that do not belong together.");
            }
            if (!totalsRight) {
                System.out.println("RESULT: LOST UPDATES. " + n(sales) + " sales of 500 should give orders=" + n(sales)
                        + " and revenue=" + n(sales * SALE_CENTS) + ", but the bean says orders=" + n(total.orders())
                        + " and revenue=" + n(total.revenueCents()) + ".");
            }
        }
    }

    // ---- shared -------------------------------------------------------------------------------

    private static void printFailures(Run run) {
        List<Reply> failures = run.failures();
        if (failures.isEmpty()) {
            return;
        }
        String message = json(failures.get(0).body(), "message");
        if (message == null) {
            message = failures.get(0).body() == null ? "" : failures.get(0).body();
        }
        if (message.length() > 80) {
            message = message.substring(0, 80) + "...";
        }
        int status = failures.get(0).status();
        line("Failed:", n(failures.size()) + "   (for example " + (status == -1 ? "no answer" : status)
                + (message.isEmpty() ? "" : ": " + message) + ")");
    }

    private static Options parse(String[] args) {
        Scenario scenario = Scenario.COUNTER;
        Integer requests = null;
        Integer products = null;
        int atOnce = 64;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--help", "-h" -> {
                    return null;
                }
                case "--scenario" -> scenario = Scenario.from(value(args, ++i, arg));
                case "--requests" -> requests = number(value(args, ++i, arg), arg, 1, 1_000_000);
                case "--threads" -> atOnce = number(value(args, ++i, arg), arg, 1, 200);
                case "--products" -> products = number(value(args, ++i, arg), arg, 1, 1_000);
                default -> throw new IllegalArgumentException("Unknown option: " + arg);
            }
        }
        int requestCount = requests == null ? scenario.defaultRequests() : requests;
        int productCount = products != null ? products : scenario == Scenario.LOCKED_CACHE ? requestCount : 1;
        return new Options(scenario, requestCount, atOnce, productCount);
    }

    private static String value(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException(option + " needs a value");
        }
        return args[index];
    }

    private static int number(String value, String option, int min, int max) {
        try {
            int number = Integer.parseInt(value.replace(",", "").replace("_", ""));
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
        System.out.println("  --scenario <name>   what to show (default: counter)");
        System.out.println("  --requests <n>      how many HTTP requests to send (default depends on the scenario)");
        System.out.println("  --threads <n>       how many requests are in flight at the same time, 1 to 200 (default: 64)");
        System.out.println("  --products <n>      price-cache and locked-cache only: spread the requests over n products");
        System.out.println();
        System.out.println("Scenarios:");
        for (Scenario scenario : Scenario.values()) {
            System.out.printf("  %-13s tasks %-8s %s%n", scenario.flag(), scenario.tasks(), scenario.summary());
        }
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  ./demo");
        System.out.println("  ./demo --threads 1                      one request at a time: nothing goes wrong");
        System.out.println("  ./demo --scenario receipt --requests 10");
    }

    private Demo() {
    }
}
