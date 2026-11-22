package com.amigoscode.sqltraps.demo;

import com.amigoscode.sqltraps.db.DatabaseSetup;
import com.amigoscode.sqltraps.db.DatabaseSetup.Shop;
import com.amigoscode.sqltraps.db.Explain;
import com.amigoscode.sqltraps.db.Plan;
import com.amigoscode.sqltraps.db.PlanFacts;
import com.amigoscode.sqltraps.shop.Query;
import com.amigoscode.sqltraps.shop.ShopQueries;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Puts the shop in a known state, runs one scenario and prints the evidence, ending with a RESULT
 * line. Run it with ./demo (or .\demo on Windows). ./demo --help lists the scenarios.
 *
 * For a report (Part A) the evidence is: the question, the SQL, the rows it returned, the rows it
 * should have returned, and the difference. For a slow query (Part B) it is the plan and the counts.
 */
public final class Demo {

    private static final int PAGE_SIZE = 20;
    private static final long BIG_SPENDER_CENTS = 10_000;
    private static final String COUNTRY = "IE";
    private static final long ONE_CUSTOMER = 6;
    private static final LocalDate ONE_DAY = LocalDate.of(2023, 2, 1);

    private enum Scenario {
        PAID_ORDERS("paid-orders", Shop.SMALL, "GET /api/reports/paid-orders",
                "Every customer, with the number of PAID orders they have. 8 customers, so 8 rows."),
        NEVER_ORDERED("never-ordered", Shop.SMALL, "GET /api/reports/never-ordered",
                "The customers who have never placed an order."),
        REVENUE("revenue", Shop.SMALL, "GET /api/reports/revenue",
                "Per customer: how many PAID orders, the revenue of those orders, the units bought."),
        BIG_SPENDERS("big-spenders", Shop.SMALL, "GET /api/reports/big-spenders?min=10000",
                "The customers whose PAID orders add up to more than 100.00."),
        COUNTRY_SPEND("country-spend", Shop.BIG, "GET /api/reports/country-spend?country=IE",
                "Every customer in Ireland, with the number of PAID orders and what they spent."),
        RECENT_ORDERS("recent-orders", Shop.BIG, "GET /api/orders/recent?size=20",
                "The newest orders, for the first page of the admin screen. The page shows 20."),
        DEV_VS_PROD("dev-vs-prod", Shop.BIG, "GET /api/customers/6/orders and GET /api/orders?day=2023-02-01",
                "The same two queries on the small shop and on the big shop."),
        STALE_STATS("stale-stats", Shop.BIG, "GET /api/orders/expired",
                "Orders that timed out unpaid, with the customer to remind. Run with stale statistics, then after ANALYZE.");

        private final String flag;
        private final Shop shop;
        private final String endpoint;
        private final String question;

        Scenario(String flag, Shop shop, String endpoint, String question) {
            this.flag = flag;
            this.shop = shop;
            this.endpoint = endpoint;
            this.question = question;
        }

        static String names() {
            return Arrays.stream(values()).map(scenario -> scenario.flag).collect(Collectors.joining(" | "));
        }
    }

    private record Options(Scenario scenario, boolean plan, boolean buffers, boolean leaveStale) {
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
        try (Connection connection = Db.connect()) {
            System.out.println();
            Scenario scenario = options.scenario();
            System.out.println("Scenario: " + scenario.flag + "     " + scenario.endpoint);
            System.out.println("Question: " + scenario.question);
            System.out.println();
            switch (scenario) {
                case PAID_ORDERS, NEVER_ORDERED, REVENUE, BIG_SPENDERS -> report(connection, options);
                case COUNTRY_SPEND -> countrySpend(connection, options);
                case RECENT_ORDERS -> recentOrders(connection, options);
                case DEV_VS_PROD -> devVsProd(connection);
                case STALE_STATS -> staleStatistics(connection, options);
            }
            System.out.println();
        }
    }

    // ------------------------------------------------------------------------------------------
    // Part A: reports on the small shop. Wrong rows, no error.
    // ------------------------------------------------------------------------------------------

    private static void report(Connection connection, Options options) throws SQLException {
        Scenario scenario = options.scenario();
        load(connection, Shop.SMALL);
        Query query = switch (scenario) {
            case PAID_ORDERS -> ShopQueries.paidOrdersPerCustomer();
            case NEVER_ORDERED -> ShopQueries.customersWhoNeverOrdered();
            case REVENUE -> ShopQueries.revenuePerCustomer();
            default -> ShopQueries.bigSpenders(BIG_SPENDER_CENTS);
        };
        List<List<String>> expected = expected(scenario);
        printSql(query);

        TextTable actual;
        try (PreparedStatement statement = prepare(connection, query)) {
            actual = TextTable.read(statement, 1000);
        }
        System.out.println("WHAT THE QUERY RETURNED (" + rows(actual.rows().size()) + ")");
        actual.print();
        System.out.println();
        System.out.println("WHAT IT SHOULD RETURN (" + rows(expected.size()) + ")");
        new TextTable(actual.columns().size() == expected.getFirst().size() ? actual.columns() : expectedColumns(scenario),
                expected).print();
        System.out.println();

        if (options.plan()) {
            printPlan(connection, query, options, "EXPLAIN ANALYZE");
            System.out.println();
        }

        List<String> difference = difference(actual, expected);
        if (difference.isEmpty()) {
            System.out.println("RESULT: OK. The report returns exactly the " + rows(expected.size()) + " it should.");
            return;
        }
        System.out.println("THE DIFFERENCE");
        difference.forEach(line -> System.out.println("  " + line));
        System.out.println();
        System.out.println("RESULT: WRONG ROWS, NO ERROR. " + headline(scenario, actual, expected));
    }

    /** The right answers, read off seed-small.sql by hand. They do not depend on any query. */
    private static List<List<String>> expected(Scenario scenario) {
        return switch (scenario) {
            case PAID_ORDERS -> List.of(
                    List.of("1", "Ada", "2"), List.of("2", "Grace", "1"), List.of("3", "Linus", "0"),
                    List.of("4", "Margaret", "0"), List.of("5", "Dennis", "0"), List.of("6", "Barbara", "3"),
                    List.of("7", "Alan", "0"), List.of("8", "Edsger", "1"));
            case NEVER_ORDERED -> List.of(List.of("4", "Margaret"), List.of("7", "Alan"));
            case REVENUE -> List.of(
                    List.of("1", "Ada", "2", "18000", "3"), List.of("2", "Grace", "1", "9000", "4"),
                    List.of("6", "Barbara", "3", "12000", "6"), List.of("8", "Edsger", "1", "9000", "2"));
            default -> List.of(List.of("1", "Ada", "18000"), List.of("6", "Barbara", "12000"));
        };
    }

    private static List<String> expectedColumns(Scenario scenario) {
        return switch (scenario) {
            case PAID_ORDERS -> List.of("id", "name", "paid_orders");
            case NEVER_ORDERED -> List.of("id", "name");
            case REVENUE -> List.of("id", "name", "orders", "revenue_cents", "units");
            default -> List.of("id", "name", "spent_cents");
        };
    }

    /** Compares the rows by their first column (the customer id): missing rows, extra rows, wrong values. */
    private static List<String> difference(TextTable actual, List<List<String>> expected) {
        List<String> lines = new ArrayList<>();
        if (actual.columns().size() != expected.getFirst().size()) {
            lines.add("The query returns " + actual.columns().size() + " columns, the report needs "
                    + expected.getFirst().size() + ".");
            return lines;
        }
        Map<String, List<String>> actualById = new LinkedHashMap<>();
        actual.rows().forEach(row -> actualById.put(row.getFirst(), row));
        for (List<String> want : expected) {
            List<String> got = actualById.remove(want.getFirst());
            String who = want.get(0) + " " + want.get(1);
            if (got == null) {
                lines.add("missing:  " + who + describe(actual.columns(), want));
                continue;
            }
            for (int c = 1; c < want.size(); c++) {
                if (!want.get(c).equals(got.get(c))) {
                    lines.add("wrong:    " + who + ": " + actual.columns().get(c) + " is " + got.get(c)
                            + ", should be " + want.get(c));
                }
            }
        }
        actualById.values().forEach(row -> lines.add("extra:    " + row.get(0) + " " + row.get(1) + " should not be in the report"));
        return lines;
    }

    private static String describe(List<String> columns, List<String> row) {
        if (row.size() <= 2) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (int c = 2; c < row.size(); c++) {
            parts.add(columns.get(c) + " " + row.get(c));
        }
        return " (" + String.join(", ", parts) + ")";
    }

    private static String headline(Scenario scenario, TextTable actual, List<List<String>> expected) {
        int got = actual.rows().size();
        int want = expected.size();
        List<String> gotIds = actual.rows().stream().map(List::getFirst).toList();
        List<String> missing = expected.stream().filter(row -> !gotIds.contains(row.getFirst())).map(row -> row.get(1)).toList();
        boolean sameShape = actual.columns().size() == expected.getFirst().size();
        if (scenario == Scenario.PAID_ORDERS && got < want) {
            return "The report shows " + got + " customers. There are " + want + ". " + missing.size()
                    + " customers without a PAID order are missing: " + String.join(", ", missing) + ".";
        }
        if (scenario == Scenario.NEVER_ORDERED && got < want) {
            return (got == 0 ? "The report is empty: it says every customer has ordered. "
                    : "The report finds " + got + " of the customers who never ordered. ")
                    + want + " customers never ordered: " + String.join(", ", missing) + ".";
        }
        if (scenario == Scenario.REVENUE && sameShape) {
            long reported = actual.rows().stream().mapToLong(row -> cents(row.get(3))).sum();
            long real = expected.stream().mapToLong(row -> cents(row.get(3))).sum();
            if (reported != real) {
                return "The report adds up to " + money(reported) + " of revenue. The PAID orders of these customers add up to "
                        + money(real) + ". " + money(Math.abs(reported - real))
                        + (reported > real ? " was counted more than once." : " is missing.");
            }
        }
        if (scenario == Scenario.BIG_SPENDERS && sameShape && got < want) {
            StringBuilder text = new StringBuilder("The report shows " + got + " of the " + want
                    + " customers who spent more than 100.00. Missing: " + String.join(", ", missing) + ".");
            for (List<String> wanted : expected) {
                actual.rows().stream()
                        .filter(row -> row.getFirst().equals(wanted.getFirst()) && !row.get(2).equals(wanted.get(2)))
                        .forEach(row -> text.append(" ").append(wanted.get(1)).append(" is shown with ")
                                .append(money(cents(row.get(2)))).append(" and spent ").append(money(cents(wanted.get(2)))).append("."));
            }
            return text.toString();
        }
        return "The report returned " + rows(got) + ". It should return " + rows(want) + ", listed above.";
    }

    // ------------------------------------------------------------------------------------------
    // Part B: slow queries on the big shop.
    // ------------------------------------------------------------------------------------------

    private static void countrySpend(Connection connection, Options options) throws SQLException {
        load(connection, Shop.BIG);
        Query query = ShopQueries.countrySpend(COUNTRY);
        printSql(query);
        Plan plan = printPlan(connection, query, options, "EXPLAIN ANALYZE");
        System.out.println();

        long orders = ShopState.number(connection, "select count(*) from orders");
        long customers = ShopState.number(connection, "select count(*) from customers");
        long ordersReads = plan.scans().stream().filter(scan -> scan.relation().equals("orders")).mapToLong(Plan.Node::loops).sum();
        if (plan.rowsRead() > orders + customers) {
            System.out.println("RESULT: SLOW. Postgres read " + number(plan.rowsRead()) + " rows to return " + number(plan.rowsReturned())
                    + ". The orders table (" + number(orders) + " rows) was read " + number(ordersReads) + " times.");
        } else {
            System.out.println("RESULT: OK. Every table was read once: " + number(plan.rowsRead()) + " rows read to return "
                    + number(plan.rowsReturned()) + ", in " + PlanFacts.millis(plan.executionMillis()) + ".");
        }
    }

    private static void recentOrders(Connection connection, Options options) throws SQLException {
        load(connection, Shop.BIG);
        Query query = ShopQueries.recentOrders(PAGE_SIZE);
        printSql(query);
        Plan plan = printPlan(connection, query, options, "EXPLAIN ANALYZE");
        System.out.println();

        // The real size of the result: every row turned into text, as it would be sent to the application.
        long rows;
        long bytes;
        int columns;
        try (PreparedStatement statement = prepare(connection, new Query(
                "select count(*), coalesce(sum(octet_length(q::text)), 0) from (" + query.sql() + ") q", query.args().toArray()));
             ResultSet row = statement.executeQuery()) {
            row.next();
            rows = row.getLong(1);
            bytes = row.getLong(2);
        }
        try (PreparedStatement statement = prepare(connection, new Query(
                "select * from (" + query.sql() + ") q limit 0", query.args().toArray()));
             ResultSet none = statement.executeQuery()) {
            columns = none.getMetaData().getColumnCount();
        }
        System.out.println("WHAT THE APPLICATION RECEIVES");
        System.out.println("  Rows:                      " + number(rows) + "   (the page shows " + PAGE_SIZE + ")");
        System.out.println("  Columns per row:           " + columns + "   (the page shows 5: id, customer_id, status, total_cents, created_at)");
        System.out.println("  Size of the result:        " + PlanFacts.bytes(bytes));
        System.out.println();
        if (rows > ShopQueries.MAX_PAGE_SIZE) {
            System.out.println("RESULT: FETCHES EVERYTHING. The page shows " + PAGE_SIZE + " orders. The query returned "
                    + number(rows) + " rows, " + PlanFacts.bytes(bytes) + ".");
        } else if (columns > 5) {
            System.out.println("RESULT: ONE PAGE OF ROWS, BUT EVERY COLUMN. " + number(rows) + " rows with " + columns
                    + " columns each, " + PlanFacts.bytes(bytes) + ". The page shows 5 columns.");
        } else {
            System.out.println("RESULT: OK. " + number(rows) + " rows, " + PlanFacts.bytes(bytes) + ". Postgres read "
                    + number(plan.rowsRead()) + " rows and stopped.");
        }
    }

    private static void devVsProd(Connection connection) throws SQLException {
        Query byCustomer = ShopQueries.ordersOfCustomer(ONE_CUSTOMER);
        Query byDay = ShopQueries.ordersOfDay(ONE_DAY);
        System.out.println("Query A: the orders of customer " + ONE_CUSTOMER + ". There is no index on orders.customer_id.");
        System.out.println();
        printSql(byCustomer);
        System.out.println("Query B: the orders of " + ONE_DAY + ". There is an index on orders.created_at.");
        System.out.println();
        printSql(byDay);

        load(connection, Shop.SMALL);
        long devOrders = ShopState.number(connection, "select count(*) from orders");
        Plan devA = Explain.analyze(connection, byCustomer);
        Plan devB = Explain.analyze(connection, byDay);
        load(connection, Shop.BIG);
        long prodOrders = ShopState.number(connection, "select count(*) from orders");
        Explain.analyze(connection, byCustomer);
        Plan prodA = Explain.analyze(connection, byCustomer);
        Plan prodB = Explain.analyze(connection, byDay);

        String dev = "\"dev\": " + number(devOrders) + " orders";
        String prod = "\"prod\": " + number(prodOrders) + " orders";
        comparison("QUERY A", dev, prod, devA, prodA);
        System.out.println();
        comparison("QUERY B", dev, prod, devB, prodB);
        System.out.println();
        System.out.println("RESULT: SAME SQL, DIFFERENT WORK. Query A has the same plan in both, and reads "
                + number(prodA.rowsRead()) + " rows in prod where it read " + number(devA.rowsRead()) + " in dev.");
        System.out.println("        Query B has a different plan: " + shortPlan(devB) + " in dev, " + shortPlan(prodB)
                + " in prod. The index is ignored on a table of one page.");
    }

    private static void comparison(String title, String dev, String prod, Plan devPlan, Plan prodPlan) {
        String row = "  %-24s %-28s %s%n";
        System.out.println(title);
        System.out.printf(row, "", dev, prod);
        System.out.printf(row, "How the rows are found", shortPlan(devPlan), shortPlan(prodPlan));
        System.out.printf(row, "Rows returned", number(devPlan.rowsReturned()), number(prodPlan.rowsReturned()));
        System.out.printf(row, "Rows read", number(devPlan.rowsRead()), number(prodPlan.rowsRead()));
        System.out.printf(row, "Pages touched", number(devPlan.pages()), number(prodPlan.pages()));
        System.out.printf(row, "Time in the database", PlanFacts.millis(devPlan.executionMillis()),
                PlanFacts.millis(prodPlan.executionMillis()));
    }

    private static String shortPlan(Plan plan) {
        Plan.Node scan = plan.scans().getFirst();
        return scan.type() + (plan.indexes().isEmpty() ? "" : " (" + plan.indexes().getFirst() + ")");
    }

    private static void staleStatistics(Connection connection, Options options) throws SQLException {
        Query query = ShopQueries.expiredOrders();
        printSql(query);
        DatabaseSetup.loadBigWithStaleStatistics(connection, System.out::println);
        System.out.println();
        ShopState.printTables(connection);
        System.out.println();
        ShopState.printStatusStatistics(connection);
        System.out.println();

        Plan stale = printPlan(connection, query, options, "EXPLAIN ANALYZE, WITH STALE STATISTICS");
        System.out.println();
        if (options.leaveStale()) {
            System.out.println("--leave-stale: ANALYZE was not run. The statistics stay stale until the next ./demo,");
            System.out.println("so you can look around with ./inspect.");
            System.out.println();
            System.out.println("RESULT: STALE STATISTICS. " + staleSentence(stale));
            return;
        }

        long start = System.nanoTime();
        DatabaseSetup.analyzeNow(connection);
        System.out.println("Now ANALYZE has run on the four tables (" + PlanFacts.millis((System.nanoTime() - start) / 1e6) + ").");
        System.out.println();
        ShopState.printStatusStatistics(connection);
        System.out.println();
        Plan fresh = printPlan(connection, query, options, "EXPLAIN ANALYZE, AFTER ANALYZE");
        System.out.println();

        System.out.println("RESULT: STALE STATISTICS. " + staleSentence(stale));
        System.out.println("        After ANALYZE the planner expected " + number(ordersScan(fresh).estimatedRows()) + " and chose a "
                + joinName(fresh) + ": " + number(fresh.pages()) + " pages, " + PlanFacts.millis(fresh.executionMillis())
                + ". Same SQL, same rows, different plan.");
    }

    private static String staleSentence(Plan plan) {
        Plan.Node orders = ordersScan(plan);
        return "The planner expected " + number(orders.estimatedRows()) + " EXPIRED " + (orders.estimatedRows() == 1 ? "order" : "orders")
                + " and got " + number(orders.actualRows()) + ". It chose a " + joinName(plan) + ": "
                + number(plan.pages()) + " pages, " + PlanFacts.millis(plan.executionMillis()) + ".";
    }

    private static Plan.Node ordersScan(Plan plan) {
        return plan.scanOf("orders").orElseThrow();
    }

    private static String joinName(Plan plan) {
        return plan.nodes().stream().filter(Plan.Node::isJoin).map(Plan.Node::type).findFirst().orElse("plan without a join");
    }

    // ------------------------------------------------------------------------------------------
    // Shared helpers.
    // ------------------------------------------------------------------------------------------

    private static void load(Connection connection, Shop shop) throws SQLException {
        boolean[] logged = new boolean[1];
        DatabaseSetup.ensure(connection, shop, message -> {
            logged[0] = true;
            System.out.println(message);
        });
        if (logged[0]) {
            System.out.println();
        }
    }

    private static void printSql(Query query) {
        query.sql().lines().forEach(line -> System.out.println("    " + line));
        if (!query.args().isEmpty()) {
            System.out.println("    -- ? = " + query.args().stream().map(Demo::literal).collect(Collectors.joining(", ")));
        }
        System.out.println();
    }

    /** Runs EXPLAIN ANALYZE twice: once for the text Postgres prints, once for the numbers. */
    private static Plan printPlan(Connection connection, Query query, Options options, String title) throws SQLException {
        List<String> text = Explain.text(connection, query, options.buffers());
        Plan plan = Explain.analyze(connection, query);
        System.out.println(title);
        text.forEach(line -> System.out.println("    " + line));
        System.out.println();
        System.out.println("WHAT STANDS OUT");
        PlanFacts.lines(plan).forEach(line -> System.out.println("  " + line));
        return plan;
    }

    private static PreparedStatement prepare(Connection connection, Query query) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(query.sql());
        for (int i = 0; i < query.args().size(); i++) {
            statement.setObject(i + 1, query.args().get(i));
        }
        return statement;
    }

    private static String literal(Object value) {
        return value instanceof Number ? String.valueOf(value) : "'" + value + "'";
    }

    private static String rows(long count) {
        return number(count) + (count == 1 ? " row" : " rows");
    }

    private static String number(long value) {
        return PlanFacts.number(value);
    }

    private static long cents(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String money(long cents) {
        return String.format(Locale.US, "%,d.%02d", cents / 100, cents % 100);
    }

    private static Options parse(String[] args) {
        Scenario scenario = Scenario.PAID_ORDERS;
        boolean plan = false;
        boolean buffers = false;
        boolean leaveStale = false;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--help", "-h" -> {
                    return null;
                }
                case "--scenario" -> {
                    if (++i >= args.length) {
                        throw new IllegalArgumentException("--scenario needs a value: " + Scenario.names());
                    }
                    scenario = scenario(args[i]);
                }
                case "--plan" -> plan = true;
                case "--buffers" -> buffers = true;
                case "--leave-stale" -> leaveStale = true;
                default -> throw new IllegalArgumentException("Unknown option: " + arg);
            }
        }
        if (leaveStale && scenario != Scenario.STALE_STATS) {
            throw new IllegalArgumentException("--leave-stale only works with --scenario stale-stats");
        }
        return new Options(scenario, plan, buffers, leaveStale);
    }

    private static Scenario scenario(String value) {
        for (Scenario scenario : Scenario.values()) {
            if (scenario.flag.equalsIgnoreCase(value)) {
                return scenario;
            }
        }
        throw new IllegalArgumentException("Unknown scenario: " + value + ". Choose one of: " + Scenario.names());
    }

    private static void printHelp() {
        System.out.println("Usage: ./demo [options]        (Windows: .\\demo [options])");
        System.out.println();
        System.out.println("Loads the shop, runs one scenario and prints the evidence, ending with a RESULT line.");
        System.out.println();
        System.out.println("  --scenario <name>   which scenario to run (default: paid-orders)");
        System.out.println();
        System.out.println("      Reports on the small shop (8 customers, 12 orders): wrong rows, no error");
        for (Scenario scenario : Scenario.values()) {
            if (scenario == Scenario.COUNTRY_SPEND) {
                System.out.println();
                System.out.println("      Slow queries on the big shop (300,012 orders)");
            }
            System.out.printf("        %-14s %s%n", scenario.flag, scenario.question);
        }
        System.out.println();
        System.out.println("  --plan              for a report: also print its EXPLAIN ANALYZE (the slow queries always do)");
        System.out.println("  --buffers           show the pages each step of the plan touched");
        System.out.println("  --leave-stale       for stale-stats: stop before ANALYZE, so you can look around with ./inspect");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  ./demo");
        System.out.println("  ./demo --scenario never-ordered");
        System.out.println("  ./demo --scenario revenue --plan");
        System.out.println("  ./demo --scenario country-spend");
        System.out.println("  ./demo --scenario stale-stats");
    }

    private Demo() {
    }
}
