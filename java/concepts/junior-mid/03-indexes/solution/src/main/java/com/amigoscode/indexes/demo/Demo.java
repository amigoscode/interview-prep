package com.amigoscode.indexes.demo;

import com.amigoscode.indexes.db.DatabaseSetup;
import com.amigoscode.indexes.db.Explain;
import com.amigoscode.indexes.db.IndexInfo;
import com.amigoscode.indexes.db.Plan;
import com.amigoscode.indexes.db.WriteCost;
import com.amigoscode.indexes.shop.Query;
import com.amigoscode.indexes.shop.ShopQueries;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Runs one of the shop's queries against the seeded database and prints how Postgres executed it:
 * the query, the EXPLAIN ANALYZE output, a summary and a RESULT line. Run it with ./demo
 * (or .\demo on Windows). ./demo --help lists the flags.
 */
public final class Demo {

    /** One of the ten big accounts: 1,000 orders. */
    private static final long BIG_CUSTOMER = 1;
    /** An ordinary customer: about 10 orders, spread over two years. */
    private static final long TYPICAL_CUSTOMER = 4242;
    private static final Pattern EXECUTION_TIME = Pattern.compile("Execution Time: ([0-9.]+) ms");

    private enum Scenario {
        TRACK("track", "GET /api/orders/track/{trackingNumber}",
                "Find one order by its tracking number."),
        LATEST_ORDERS("latest-orders", "GET /api/customers/{id}/orders",
                "The 5 newest orders of one customer (default: customer 4242, who has 10 orders)."),
        EMAIL("email", "GET /api/customers?email=CUSTOMER4242@SHOP.TEST",
                "Find a customer by email, whatever case it was typed in."),
        DAILY_SALES("daily-sales", "GET /api/reports/daily-sales?day=2025-06-15",
                "Number of orders and revenue for one day."),
        PENDING("pending", "GET /api/orders/pending",
                "The warehouse queue: the 50 oldest orders that are still PENDING."),
        SPEND("spend", "GET /api/customers/{id}/spend",
                "How many orders a customer placed and what they spent (default: customer 1, who has 1,000)."),
        BY_STATUS("by-status", "GET /api/reports/by-status?status=...",
                "Revenue of every order in one status. Choose it with --status."),
        LIKE("like", "(no endpoint yet: someone wants a search box)",
                "Search tracking numbers with LIKE. Choose the pattern with --pattern."),
        NUMERIC_ID("numeric-id", "GET /api/customers/{id}/spend",
                "The same query as spend, but the id is sent as a decimal (BigDecimal), not a long.");

        private final String flag;
        private final String endpoint;
        private final String description;

        Scenario(String flag, String endpoint, String description) {
            this.flag = flag;
            this.endpoint = endpoint;
            this.description = description;
        }

        static String names() {
            return Arrays.stream(values()).map(scenario -> scenario.flag).collect(Collectors.joining(" | "));
        }
    }

    private record Options(Scenario scenario, boolean noIndexes, boolean buffers, boolean afterUpdate,
                           boolean writeCost, boolean reseed, int rows, String status, String pattern, Long customer) {

        long customerOr(long fallback) {
            return customer == null ? fallback : customer;
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
        try (Connection connection = Db.connect()) {
            System.out.println();
            if (options.reseed()) {
                DatabaseSetup.forget(connection);
            }
            boolean[] logged = new boolean[1];
            try {
                DatabaseSetup.run(connection, DatabaseSetup.DEFAULT_ORDERS, message -> {
                    logged[0] = true;
                    System.out.println(message);
                });
                if (logged[0]) {
                    System.out.println();
                }
            } catch (DatabaseSetup.SetupException e) {
                System.out.println(e.getMessage());
                System.out.println();
                System.out.println("Fix the statement and run the command again.");
                System.out.println();
                return;
            }
            if (options.writeCost()) {
                writeCost(connection, options);
            } else {
                run(connection, options);
            }
        }
    }

    private static void run(Connection connection, Options options) throws SQLException {
        Scenario scenario = options.scenario();
        Query query = query(connection, options);

        System.out.println("Query: " + scenario.flag + "     " + scenario.endpoint);
        System.out.println(scenario.description);
        System.out.println();
        query.sql().lines().forEach(line -> System.out.println("    " + line));
        if (!query.args().isEmpty()) {
            System.out.println("    -- ? = " + query.args().stream().map(Demo::literal).collect(Collectors.joining(", ")));
        }
        System.out.println();

        connection.setAutoCommit(false);
        Plan plan;
        List<IndexInfo> indexes;
        try {
            try (Statement statement = connection.createStatement()) {
                if (options.noIndexes()) {
                    for (IndexInfo index : IndexInfo.all(connection)) {
                        if (index.secondary()) {
                            statement.execute("DROP INDEX \"" + index.name() + "\"");
                        }
                    }
                    System.out.println("--no-indexes: for this run only, every index except the primary keys is hidden.");
                }
                if (scenario == Scenario.BY_STATUS) {
                    statement.execute("CREATE INDEX idx_tmp_orders_status ON orders (status)");
                    System.out.println("For this run only, the table also has: CREATE INDEX idx_tmp_orders_status ON orders (status)");
                }
            }
            indexes = IndexInfo.all(connection);
            printTable(connection, scenario == Scenario.EMAIL ? "customers" : "orders", indexes);

            if (options.afterUpdate()) {
                // Committed on its own, so that it is the table's real state when the query runs.
                connection.rollback();
                connection.setAutoCommit(true);
                long customer = options.customerOr(BIG_CUSTOMER);
                try (Statement statement = connection.createStatement()) {
                    int updated = statement.executeUpdate(
                            "UPDATE orders SET total_cents = total_cents WHERE customer_id = " + customer);
                    System.out.println();
                    System.out.println("--after-update: " + number(updated) + " orders of customer " + customer
                            + " were just updated (to the same values). No VACUUM has run since.");
                    explain(connection, query, options, "EXPLAIN ANALYZE, right after the update");
                    statement.execute("VACUUM orders");
                    System.out.println();
                    System.out.println("Now VACUUM orders has run.");
                }
            }
            plan = explain(connection, query, options, options.afterUpdate() ? "EXPLAIN ANALYZE, after VACUUM" : "EXPLAIN ANALYZE");
        } finally {
            if (!connection.getAutoCommit()) {
                connection.rollback();
                connection.setAutoCommit(true);
            }
        }

        System.out.println();
        System.out.println(verdict(connection, options, plan, indexes));
        System.out.println();
    }

    /** Runs EXPLAIN ANALYZE, prints the plan Postgres printed and a summary of it. */
    private static Plan explain(Connection connection, Query query, Options options, String title) throws SQLException {
        // Three runs: one to fill the cache, one to print, one to take the numbers from.
        Explain.analyze(connection, query);
        List<String> text = Explain.text(connection, query, options.buffers());
        Plan plan = Explain.analyze(connection, query);

        System.out.println();
        System.out.println(title);
        text.forEach(line -> System.out.println("    " + line));

        String time = text.stream().map(EXECUTION_TIME::matcher).filter(Matcher::find).map(m -> m.group(1))
                .findFirst().orElse(String.format(Locale.US, "%.3f", plan.executionMillis()));
        System.out.println();
        System.out.println("How the rows were found:    " + plan.describe());
        System.out.println("Rows returned:              " + number(plan.rowsReturned()));
        System.out.println("Rows read:                  " + number(plan.rowsRead()));
        System.out.println("Rows read and thrown away:  " + number(plan.rowsThrownAway()));
        if (plan.has("Index Only Scan")) {
            System.out.println("Heap fetches (table visits): " + number(plan.heapFetches()));
        }
        System.out.println("Pages read (8 kB each):     " + number(plan.pages()));
        System.out.println("Time in the database:       " + time + " ms");
        return plan;
    }

    private static String verdict(Connection connection, Options options, Plan plan, List<IndexInfo> indexes)
            throws SQLException {
        long read = plan.rowsRead();
        long returned = plan.rowsReturned();
        long thrownAway = plan.rowsThrownAway();
        boolean seqScan = plan.has("Seq Scan");

        if (options.scenario() == Scenario.BY_STATUS) {
            long matching = count(connection, "SELECT count(*) FROM orders WHERE status = ?", options.status());
            long all = count(connection, "SELECT count(*) FROM orders");
            String share = String.format(Locale.US, "%.1f%%", 100.0 * matching / all);
            if (seqScan) {
                return "RESULT: SEQ SCAN, AND THAT IS THE RIGHT PLAN. status = '" + options.status() + "' matches "
                        + number(matching) + " of " + number(all) + " rows (" + share + ").\n"
                        + "        Reading the whole table once is cheaper than visiting that many rows through the index.";
            }
            return "RESULT: INDEX USED. status = '" + options.status() + "' matches " + number(matching) + " of "
                    + number(all) + " rows (" + share + "), few enough for the index to pay off.";
        }
        if (seqScan) {
            return "RESULT: SEQ SCAN. Postgres read " + number(read) + " rows to return " + number(returned) + ".";
        }
        if (thrownAway >= 1000 && thrownAway > 10 * returned) {
            return "RESULT: INDEX USED, BUT WASTEFUL. Postgres read " + number(read) + " rows and threw away "
                    + number(thrownAway) + " to return " + number(returned) + ".";
        }
        if (plan.pages() >= 500 && plan.pages() > 20 * Math.max(1, read)) {
            return "RESULT: INDEX USED, BUT WALKED FROM THE END, NOT SEARCHED. Postgres read " + number(plan.pages())
                    + " pages to find " + number(read) + " rows.";
        }
        if (options.scenario() == Scenario.LATEST_ORDERS && plan.has("Sort")) {
            return "RESULT: INDEX USED, BUT IT STILL SORTS. Postgres read " + number(read)
                    + " rows and sorted them to return " + number(returned) + ".";
        }
        if (options.scenario() == Scenario.SPEND) {
            if (!plan.has("Index Only Scan")) {
                return "RESULT: INDEX USED, BUT THE TABLE IS STILL VISITED. " + number(read)
                        + " rows were fetched from the table to add up one column.";
            }
            if (plan.heapFetches() > 0) {
                return "RESULT: INDEX ONLY SCAN, BUT " + number(plan.heapFetches()) + " HEAP FETCHES. "
                        + "The visibility map is out of date, so Postgres had to check the table. VACUUM fixes that.";
            }
            return "RESULT: OK. " + plan.describe() + ". The table was not visited at all.";
        }
        if (options.scenario() == Scenario.PENDING) {
            boolean partial = indexes.stream().anyMatch(index -> index.partial() && plan.indexes().contains(index.name()));
            if (!partial) {
                return "RESULT: INDEX USED, BUT IT IS NOT PARTIAL. " + plan.indexes()
                        + " holds an entry for every order, to serve a query that only wants the PENDING ones.";
            }
        }
        return "RESULT: OK. " + plan.describe() + ". Postgres read " + number(read) + " "
                + (read == 1 ? "row" : "rows") + " to return " + number(returned) + ".";
    }

    private static Query query(Connection connection, Options options) throws SQLException {
        return switch (options.scenario()) {
            case TRACK -> ShopQueries.trackOrder(trackingNumberOfAnOrderInTheMiddle(connection));
            case LATEST_ORDERS -> ShopQueries.latestOrders(options.customerOr(TYPICAL_CUSTOMER));
            case EMAIL -> ShopQueries.customerByEmail("CUSTOMER4242@SHOP.TEST");
            case DAILY_SALES -> ShopQueries.dailySales(LocalDate.of(2025, 6, 15));
            case PENDING -> ShopQueries.pendingQueue();
            case SPEND -> ShopQueries.customerSpend(options.customerOr(BIG_CUSTOMER));
            case BY_STATUS -> ShopQueries.salesByStatus(options.status());
            case LIKE -> new Query("""
                    SELECT id, tracking_number
                      FROM orders
                     WHERE tracking_number LIKE ?
                    """, options.pattern());
            case NUMERIC_ID -> new Query(ShopQueries.customerSpend(BIG_CUSTOMER).sql(),
                    new BigDecimal(options.customerOr(BIG_CUSTOMER)));
        };
    }

    private static String trackingNumberOfAnOrderInTheMiddle(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery(
                     "SELECT tracking_number FROM orders WHERE id = (SELECT max(id) FROM orders) * 7 / 9")) {
            row.next();
            return row.getString(1);
        }
    }

    private static void printTable(Connection connection, String table, List<IndexInfo> indexes) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery("SELECT count(*), pg_relation_size('" + table + "') FROM " + table)) {
            row.next();
            System.out.println("Table " + table + ": " + number(row.getLong(1)) + " rows, " + megabytes(row.getLong(2)) + ".");
        }
        System.out.println("Indexes on " + table + ":");
        for (IndexInfo index : indexes) {
            if (index.table().equals(table)) {
                System.out.printf("    %-30s %s%n", index.name(), index.columns());
            }
        }
    }

    private static void writeCost(Connection connection, Options options) throws SQLException {
        System.out.println("Write cost: the same " + number(options.rows())
                + " orders are inserted into two empty copies of the orders table.");
        System.out.println("One copy has no index. The other has these six:");
        System.out.println();
        WriteCost.INDEXES.forEach(index -> System.out.println("    " + index));
        System.out.println();

        WriteCost.Result result = WriteCost.run(connection, options.rows());

        String row = "  %-22s %14s %14s%n";
        System.out.printf(row, "", "no index", "6 indexes");
        System.out.printf(row, "Rows inserted", number(result.rows()), number(result.rows()));
        System.out.printf(row, "Time to insert", number(result.bareMillis()) + " ms", number(result.indexedMillis()) + " ms");
        System.out.printf(row, "Size of the rows", megabytes(result.tableBytes()), megabytes(result.tableBytes()));
        System.out.printf(row, "Size of the indexes", megabytes(result.bareIndexBytes()), megabytes(result.indexedIndexBytes()));
        System.out.println();
        System.out.printf(Locale.US, "RESULT: SIX INDEXES MADE THE SAME INSERT %.1fx SLOWER and added %s of indexes to %s of rows.%n",
                (double) result.indexedMillis() / Math.max(1, result.bareMillis()),
                megabytes(result.indexedIndexBytes()), megabytes(result.tableBytes()));
        System.out.println("        Every INSERT has to add one entry to every index. So does every UPDATE of an indexed column.");
        System.out.println();
    }

    private static long count(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return row.getLong(1);
            }
        }
    }

    private static String literal(Object value) {
        return value instanceof Number ? value + " (" + value.getClass().getSimpleName() + ")" : "'" + value + "'";
    }

    private static String number(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    private static String megabytes(long bytes) {
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private static Options parse(String[] args) {
        Scenario scenario = Scenario.TRACK;
        boolean noIndexes = false;
        boolean buffers = false;
        boolean afterUpdate = false;
        boolean writeCost = false;
        boolean reseed = false;
        int rows = 200_000;
        String status = "DELIVERED";
        String pattern = "%ABC";
        Long customer = null;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--help", "-h" -> {
                    return null;
                }
                case "--query" -> scenario = scenario(value(args, ++i, arg));
                case "--no-indexes" -> noIndexes = true;
                case "--buffers" -> buffers = true;
                case "--after-update" -> afterUpdate = true;
                case "--write-cost" -> writeCost = true;
                case "--reseed" -> reseed = true;
                case "--rows" -> rows = number(value(args, ++i, arg), arg, 1_000, 1_000_000);
                case "--status" -> status = status(value(args, ++i, arg));
                case "--pattern" -> pattern = value(args, ++i, arg);
                case "--customer" -> customer = (long) number(value(args, ++i, arg), arg, 1, 100_000);
                default -> throw new IllegalArgumentException("Unknown option: " + arg);
            }
        }
        if (afterUpdate && scenario != Scenario.SPEND) {
            throw new IllegalArgumentException("--after-update only works with --query spend");
        }
        if (afterUpdate && noIndexes) {
            throw new IllegalArgumentException("--after-update and --no-indexes cannot be combined");
        }
        return new Options(scenario, noIndexes, buffers, afterUpdate, writeCost, reseed, rows, status, pattern, customer);
    }

    private static Scenario scenario(String value) {
        for (Scenario scenario : Scenario.values()) {
            if (scenario.flag.equalsIgnoreCase(value)) {
                return scenario;
            }
        }
        throw new IllegalArgumentException("Unknown query: " + value + ". Choose one of: " + Scenario.names());
    }

    private static String status(String value) {
        String status = value.toUpperCase(Locale.ROOT);
        if (!List.of("DELIVERED", "CANCELLED", "SHIPPED", "PENDING").contains(status)) {
            throw new IllegalArgumentException("--status must be DELIVERED, CANCELLED, SHIPPED or PENDING, got: " + value);
        }
        return status;
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
        System.out.println("Runs one query of the shop with EXPLAIN ANALYZE and shows how Postgres found the rows.");
        System.out.println();
        System.out.println("  --query <name>    which query to run (default: track)");
        for (Scenario scenario : Scenario.values()) {
            System.out.printf("                      %-14s %s%n", scenario.flag, scenario.description);
        }
        System.out.println("  --no-indexes      hide every index except the primary keys, for this run only");
        System.out.println("  --buffers         also show how many pages each step of the plan read");
        System.out.println("  --status <s>      for --query by-status: DELIVERED | CANCELLED | SHIPPED | PENDING (default: DELIVERED)");
        System.out.println("  --customer <id>   for latest-orders, spend and numeric-id: which customer (1 to 10 are big accounts)");
        System.out.println("  --pattern <p>     for --query like: the LIKE pattern (default: %ABC)");
        System.out.println("  --after-update    for --query spend: update the customer's orders first, then show the plan");
        System.out.println("                    before and after VACUUM");
        System.out.println("  --write-cost      insert the same rows into a table with no index and one with six, and compare");
        System.out.println("  --rows <n>        for --write-cost: how many rows to insert (default: 200000)");
        System.out.println("  --reseed          empty the tables and seed the 1,000,000 orders again");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  ./demo");
        System.out.println("  ./demo --query latest-orders");
        System.out.println("  ./demo --query track --no-indexes");
        System.out.println("  ./demo --query by-status --status PENDING");
        System.out.println("  ./demo --write-cost");
    }

    private Demo() {
    }
}
