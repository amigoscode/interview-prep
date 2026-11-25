package com.amigoscode.pagination.demo;

import static com.amigoscode.pagination.demo.App.number;

import com.amigoscode.pagination.db.Plan;
import com.amigoscode.pagination.db.SqlStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Starts the real application, asks it for pages of orders over HTTP, and prints what each request
 * cost inside Postgres: the SQL it sent, the rows that SQL read and the pages it touched. Ends with
 * a RESULT line. Run it with ./demo (or .\demo on Windows). ./demo --help lists the flags.
 */
public final class Demo {

    private enum Mode {
        OFFSET, KEYSET, HISTORY
    }

    private record Options(Mode mode, int depth, int size, boolean insert, long customer, boolean reseed) {
    }

    /** What Postgres did for the statement that fetched the rows of one page. */
    private record Cost(App.Response response, Plan plan) {

        long thrownAway() {
            return Math.max(0, plan.rowsRead() - response.ids().size());
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
        try (App app = App.start(options.reseed())) {
            if (app == null) {
                return;
            }
            if (options.size() > 100) {
                unbounded(app, options);
            } else if (options.mode() == Mode.HISTORY) {
                history(app, options);
            } else if (options.insert()) {
                shift(app, options);
            } else {
                depth(app, options);
            }
            System.out.println();
        }
    }

    // ---- Scenario 1: how much does a deep page cost? ----

    private static void depth(App app, Options options) throws Exception {
        int size = options.size();
        long pages = app.orders() / size;
        int depth = (int) Math.min(options.depth(), pages);
        long ordersBefore = (long) (depth - 1) * size;
        boolean keyset = options.mode() == Mode.KEYSET;

        System.out.println("Mode: " + name(options.mode()) + " | page size: " + size + " | depth: page " + number(depth)
                + " of " + number(pages));
        System.out.println("Table orders: " + number(app.orders()) + " rows, newest first.");

        System.out.println();
        System.out.println("--- Page 1 ---");
        Cost first = request(app, keyset ? "/api/orders/feed?size=" + size : "/api/orders?page=0&size=" + size);
        if (first == null) {
            return;
        }
        List<String> problems = new ArrayList<>();

        if (keyset) {
            System.out.println();
            System.out.println("--- Page 2: the client sends back the nextCursor of page 1 ---");
            String cursor = first.response().nextCursor();
            if (cursor == null) {
                System.out.println("Page 1 has no nextCursor, so the client believes the feed ends here.");
                problems.add("Page 1 came back without a nextCursor although " + number(app.orders() - size) + " orders follow it.");
            } else {
                Cost second = request(app, App.feedPath(cursor, size));
                if (second == null) {
                    return;
                }
                Set<Long> missing = new LinkedHashSet<>(app.expectedIds(0, 2 * size));
                missing.removeAll(first.response().ids());
                missing.removeAll(second.response().ids());
                if (!missing.isEmpty()) {
                    problems.add("After two pages a client should have seen the " + (2 * size) + " newest orders. "
                            + number(missing.size()) + " of them were never returned, for example #"
                            + number(missing.iterator().next()) + ".");
                }
            }
        }

        Cost deep = first;
        if (depth > 1) {
            System.out.println();
            System.out.println("--- Page " + number(depth) + " ---");
            if (keyset) {
                System.out.println("A client gets here by following nextCursor " + number(depth - 1) + " times. To save you the wait, the");
                System.out.println("demo looked up the last order of page " + number(depth - 1) + " in the database and built the cursor from it.");
            }
            deep = request(app, keyset ? app.feedPathAfter(ordersBefore, size) : "/api/orders?page=" + (depth - 1) + "&size=" + size);
            if (deep == null) {
                return;
            }
            if (keyset) {
                Set<Long> missing = new LinkedHashSet<>(app.expectedIds(ordersBefore, size));
                missing.removeAll(deep.response().ids());
                if (!missing.isEmpty()) {
                    problems.add("Page " + number(depth) + " comes after " + number(ordersBefore) + " orders, so it should hold the next "
                            + size + ". " + number(missing.size()) + " of them were not returned, for example #"
                            + number(missing.iterator().next()) + ".");
                }
            }
            System.out.println();
            compare(first, deep, depth);
        }

        System.out.println();
        if (!problems.isEmpty()) {
            problems.forEach(problem -> System.out.println("RESULT: SKIPPED ROWS. " + problem));
        } else if (keyset && (deep.plan().has("Sort") || deep.plan().rowsRead() > size + 1)) {
            System.out.println("RESULT: NO MATCHING INDEX. The pages are right, but page " + number(depth) + " read "
                    + number(deep.plan().rowsRead()) + " rows" + (deep.plan().has("Sort") ? " and sorted them" : "") + " to return "
                    + deep.response().ids().size() + ".");
            System.out.println("        The feed is sorted by (created_at, id). No index on orders has exactly those columns.");
        } else if (!keyset && deep.plan().rowsRead() > 10L * (size + 1)) {
            System.out.println("RESULT: DEEP OFFSET. Page " + number(depth) + " read " + number(deep.plan().rowsRead())
                    + " rows to return " + deep.response().ids().size() + ". Page 1 read " + number(first.plan().rowsRead()) + ".");
        } else {
            System.out.println("RESULT: OK. Page " + number(depth) + " read " + number(deep.plan().rowsRead()) + " rows to return "
                    + deep.response().ids().size() + (depth > 1 ? ". Page 1 read " + number(first.plan().rowsRead()) + "." : "."));
        }
        hiddenCount(app, deep.response());
    }

    private static void compare(Cost first, Cost deep, int depth) {
        String header = "page " + number(depth);
        String format = "%-30s%-14s%s%n";
        System.out.printf(format, "", "page 1", header);
        System.out.printf(format, "Rows returned to the client", first.response().ids().size(), deep.response().ids().size());
        System.out.printf(format, "Rows read", number(first.plan().rowsRead()), number(deep.plan().rowsRead()));
        System.out.printf(format, "Rows read and thrown away", number(first.thrownAway()), number(deep.thrownAway()));
        System.out.printf(format, "Pages read (8 kB each)", number(first.plan().pages()), number(deep.plan().pages()));
        System.out.printf(format, "Time in the database", millis(first.plan()), millis(deep.plan()) + "   (differs on every run)");
    }

    // ---- Scenario 2: an order is placed between two page requests ----

    private static void shift(App app, Options options) throws Exception {
        int size = options.size();
        boolean keyset = options.mode() == Mode.KEYSET;
        System.out.println("Mode: " + name(options.mode()) + " | page size: " + size
                + " | an order is placed between the two requests");

        List<Long> expected = app.expectedIds(0, 2 * size);
        long newOrder = app.orders() + 1;
        try {
            System.out.println();
            App.Response first = show(app, keyset ? "/api/orders/feed?size=" + size : "/api/orders?page=0&size=" + size);
            if (first == null) {
                return;
            }
            System.out.println("    last 3 orders:   " + orders(tail(first.ids(), 3)));

            System.out.println();
            placeOrder(app, newOrder);
            System.out.println("A customer places order #" + number(newOrder) + ". It is now the newest order: first in the list.");
            System.out.println();

            String next;
            if (!keyset) {
                next = "/api/orders?page=1&size=" + size;
            } else if (first.nextCursor() != null) {
                next = App.feedPath(first.nextCursor(), size);
            } else {
                System.out.println("RESULT: SKIPPED ROWS. Page 1 came back without a nextCursor, so the client stops reading.");
                return;
            }
            App.Response second = show(app, next);
            if (second == null) {
                return;
            }
            System.out.println("    first 3 orders:  " + orders(second.ids().subList(0, Math.min(3, second.ids().size()))));

            Set<Long> twice = new LinkedHashSet<>(first.ids());
            twice.retainAll(second.ids());
            Set<Long> missing = new LinkedHashSet<>(expected);
            missing.removeAll(first.ids());
            missing.removeAll(second.ids());

            System.out.println();
            if (!twice.isEmpty()) {
                long example = twice.iterator().next();
                System.out.println("RESULT: SHIFTED. Order #" + number(example) + " was returned twice: at the end of page 1 and again"
                        + " at the start of page 2.");
                System.out.println("        The new order moved every other order down by one, and OFFSET " + size
                        + " counts from the new top.");
            } else if (!missing.isEmpty()) {
                System.out.println("RESULT: SKIPPED ROWS. " + number(missing.size()) + " of the " + (2 * size)
                        + " newest orders were on neither page, for example #" + number(missing.iterator().next()) + ".");
            } else {
                System.out.println("RESULT: OK. Page 2 continues exactly where page 1 ended. No order was returned twice or skipped.");
            }
            hiddenCount(app, second);
        } finally {
            try (Statement statement = app.connection().createStatement()) {
                statement.execute("delete from orders where id = " + newOrder);
            }
        }
    }

    private static void placeOrder(App app, long id) throws SQLException {
        try (PreparedStatement insert = app.connection().prepareStatement("""
                insert into orders (id, customer_id, status, total_cents, created_at)
                select ?, 1001, 'PENDING', 1999, max(created_at) + interval '1 minute' from orders
                """)) {
            insert.setLong(1, id);
            insert.executeUpdate();
        }
    }

    // ---- Scenario 3: read all orders of one customer, page by page ----

    private static void history(App app, Options options) throws Exception {
        int size = options.size();
        long customer = options.customer();
        List<Long> expected = new ArrayList<>();
        try (PreparedStatement select = app.connection().prepareStatement("select id from orders where customer_id = ?")) {
            select.setLong(1, customer);
            try (ResultSet row = select.executeQuery()) {
                while (row.next()) {
                    expected.add(row.getLong(1));
                }
            }
        }
        System.out.println("Mode: history | customer: " + customer + " | page size: " + size);
        System.out.println("Customer " + customer + " has " + number(expected.size()) + " orders. The demo reads all of them, page by page.");
        System.out.println("Nothing is inserted, updated or deleted while it reads.");
        System.out.println();

        String path = "/api/customers/" + customer + "/orders?size=" + size + "&page=";
        Cost firstPage = request(app, path + 0);
        if (firstPage == null) {
            return;
        }
        List<Long> received = new ArrayList<>(firstPage.response().ids());
        List<List<Long>> pages = new ArrayList<>(List.of(firstPage.response().ids()));
        App.Response last = firstPage.response();
        while (last.hasNext() && !last.ids().isEmpty() && pages.size() < 10_000) {
            last = app.get(path + pages.size());
            if (!last.ok()) {
                System.out.println("GET " + last.path() + " returned " + last.status() + " " + last.error());
                System.out.println();
                System.out.println("RESULT: FAILED. The demo could not read page " + pages.size() + ".");
                return;
            }
            pages.add(last.ids());
            received.addAll(last.ids());
        }
        if (pages.size() > 1) {
            System.out.println();
            System.out.println("... and " + (pages.size() - 1) + " more requests, for page=1 to page=" + (pages.size() - 1) + ".");
        }

        Set<Long> different = new LinkedHashSet<>(received);
        Set<Long> never = new LinkedHashSet<>(expected);
        never.removeAll(different);
        int twice = received.size() - different.size();

        System.out.println();
        System.out.printf("%-30s%s%n", "Pages requested", pages.size());
        System.out.printf("%-30s%s%n", "Orders received", number(received.size()));
        System.out.printf("%-30s%s%n", "Different orders received", number(different.size()));
        System.out.printf("%-30s%s%n", "Orders received twice", number(twice));
        System.out.printf("%-30s%s%n", "Orders never received", number(never.size()));
        if (twice > 0) {
            System.out.println();
            System.out.println(repeatExample(app, pages));
        }

        System.out.println();
        if (twice > 0 || !never.isEmpty()) {
            System.out.println("RESULT: UNSTABLE ORDER. Nothing changed in the database, but " + number(twice)
                    + " orders came back twice and " + number(never.size()) + " never came back.");
        } else {
            System.out.println("RESULT: OK. " + number(expected.size()) + " orders, each returned exactly once.");
        }
        hiddenCount(app, firstPage.response());
    }

    /** "Order #921,646 was on page 0 and again on page 1. 11 other orders of this customer have the same created_at." */
    private static String repeatExample(App app, List<List<Long>> pages) throws SQLException {
        for (int later = 1; later < pages.size(); later++) {
            for (long id : pages.get(later)) {
                for (int earlier = 0; earlier < later; earlier++) {
                    if (pages.get(earlier).contains(id)) {
                        try (PreparedStatement select = app.connection().prepareStatement("""
                                select count(*) - 1 from orders o
                                 where (o.customer_id, o.created_at) = (select customer_id, created_at from orders where id = ?)
                                """)) {
                            select.setLong(1, id);
                            try (ResultSet row = select.executeQuery()) {
                                row.next();
                                return "For example: order #" + number(id) + " was on page=" + earlier + " and again on page=" + later
                                        + ".\n" + number(row.getLong(1)) + " other orders of this customer have exactly the same created_at.";
                            }
                        }
                    }
                }
            }
        }
        return "";
    }

    // ---- Scenario 4: a page size nobody should be allowed to ask for ----

    private static void unbounded(App app, Options options) throws Exception {
        int size = options.size();
        System.out.println("Mode: " + name(options.mode()) + " | page size: " + number(size));
        System.out.println();
        String path = switch (options.mode()) {
            case OFFSET -> "/api/orders?page=0&size=" + size;
            case KEYSET -> "/api/orders/feed?size=" + size;
            case HISTORY -> "/api/customers/" + options.customer() + "/orders?page=0&size=" + size;
        };
        App.Response response = app.get(path);
        System.out.println("GET " + path);
        if (response.ok()) {
            System.out.println("    200 OK, " + number(response.ids().size()) + " orders, "
                    + megabytes(response.bytes()) + " of JSON, " + number(response.millis()) + " ms");
            System.out.println();
            System.out.println("RESULT: UNBOUNDED. One request made the server load " + number(response.ids().size())
                    + " orders and send " + megabytes(response.bytes()) + ".");
            System.out.println("        Nothing stops a client from asking for size=1000000, or from leaving size out.");
        } else {
            System.out.println("    " + response.status() + " " + response.error());
            System.out.println("    SQL statements sent: " + response.statements().size());
            System.out.println();
            if (response.status() == 400) {
                System.out.println("RESULT: OK. size=" + size + " was rejected with a 400 before anything was read from the database.");
            } else {
                System.out.println("RESULT: FAILED. size=" + size + " should be a 400 (the client's mistake), not a " + response.status() + ".");
            }
        }
    }

    // ---- Shared printing ----

    /** Sends the request, prints it with the cost of every SQL statement, and returns the cost of the page query. */
    private static Cost request(App app, String path) throws Exception {
        App.Response response = show(app, path);
        if (response == null) {
            return null;
        }
        Plan pagePlan = null;
        System.out.println("    SQL statements sent: " + response.statements().size());
        int position = 1;
        for (SqlStatement statement : response.statements()) {
            Plan plan = app.plan(statement);
            if (pagePlan == null && !statement.isCount()) {
                pagePlan = plan;
            }
            String values = App.values(statement);
            System.out.println("      " + position++ + ". " + statement.shortSql() + (values.isEmpty() ? "" : "     -- ? = " + values));
            System.out.println("         " + plan.describe());
            System.out.println("         rows read: " + number(plan.rowsRead()) + " | rows returned: " + number(plan.rowsReturned())
                    + " | pages read: " + number(plan.pages()));
        }
        if (pagePlan == null) {
            System.out.println();
            System.out.println("RESULT: FAILED. The request sent no SQL that fetches orders.");
            return null;
        }
        return new Cost(response, pagePlan);
    }

    /** Sends the request and prints its first lines. Returns null, after printing a RESULT line, if it is not a 200. */
    private static App.Response show(App app, String path) throws Exception {
        App.Response response = app.get(path);
        System.out.println("GET " + path);
        if (!response.ok()) {
            System.out.println("    " + response.status() + " " + response.error());
            System.out.println();
            System.out.println("RESULT: FAILED. The request returned " + response.status() + ".");
            return null;
        }
        System.out.println("    200 OK, " + response.ids().size() + " orders, " + response.millis() + " ms");
        return response;
    }

    private static void hiddenCount(App app, App.Response response) throws SQLException {
        if (response.countQueries().isEmpty()) {
            return;
        }
        Plan count = app.plan(response.countQueries().getFirst());
        System.out.println("RESULT: HIDDEN COUNT. The request also sent a SELECT COUNT that nobody asked for. It read "
                + number(count.rowsRead()) + " rows to return one number.");
    }

    private static String orders(List<Long> ids) {
        StringBuilder text = new StringBuilder();
        for (long id : ids) {
            text.append('#').append(number(id)).append("  ");
        }
        return text.toString().stripTrailing();
    }

    private static <T> List<T> tail(List<T> list, int count) {
        return list.subList(Math.max(0, list.size() - count), list.size());
    }

    private static String millis(Plan plan) {
        return String.format(Locale.US, "%.2f ms", plan.executionMillis());
    }

    private static String megabytes(int bytes) {
        return String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0);
    }

    private static String name(Mode mode) {
        return mode.name().toLowerCase(Locale.ROOT);
    }

    // ---- Options ----

    private static Options parse(String[] args) {
        Mode mode = Mode.OFFSET;
        int depth = 5_000;
        int size = 20;
        boolean insert = false;
        long customer = 42;
        boolean reseed = false;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--help", "-h" -> {
                    return null;
                }
                case "--mode" -> mode = mode(value(args, ++i, arg));
                case "--depth" -> depth = between(value(args, ++i, arg), arg, 1, 1_000_000);
                case "--size" -> size = between(value(args, ++i, arg), arg, 1, 1_000_000);
                case "--customer" -> customer = between(value(args, ++i, arg), arg, 1, 100_000);
                case "--insert" -> insert = true;
                case "--reseed" -> reseed = true;
                default -> throw new IllegalArgumentException("Unknown option: " + arg);
            }
        }
        if (insert && mode == Mode.HISTORY) {
            throw new IllegalArgumentException("--insert works with --mode offset and --mode keyset");
        }
        return new Options(mode, depth, size, insert, customer, reseed);
    }

    private static Mode mode(String value) {
        try {
            return Mode.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("--mode must be offset, keyset or history, got: " + value);
        }
    }

    private static String value(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException(option + " needs a value");
        }
        return args[index];
    }

    private static int between(String value, String option, int min, int max) {
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
        System.out.println("  --mode <name>     offset | keyset | history (default: offset)");
        System.out.println("                      offset   GET /api/orders?page=&size=           every order, LIMIT and OFFSET");
        System.out.println("                      keyset   GET /api/orders/feed?after=&size=     every order, with a cursor");
        System.out.println("                      history  GET /api/customers/{id}/orders        reads ALL orders of one customer");
        System.out.println("  --depth <n>       which page to compare with page 1 (default: 5000)");
        System.out.println("  --size <n>        orders per page (default: 20). Above 100 the demo only asks for that one page");
        System.out.println("  --insert          place a new order between the request for page 1 and the request for page 2");
        System.out.println("  --customer <id>   the customer for --mode history (default: 42, who has 300 orders)");
        System.out.println("  --reseed          empty the table and seed the 1,000,000 orders again");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  ./demo");
        System.out.println("  ./demo --size 100000");
        System.out.println("  ./demo --mode history");
        System.out.println("  ./demo --insert");
        System.out.println("  ./demo --mode keyset --depth 5000");
        System.out.println("  ./demo --mode keyset --insert");
    }

    private Demo() {
    }
}
