package com.amigoscode.pagination.demo;

import static com.amigoscode.pagination.demo.App.number;

import com.amigoscode.pagination.db.Plan;
import com.amigoscode.pagination.db.SqlStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shows what Postgres does for one page request: the table and its indexes, the SQL the endpoint
 * sends right now, and the full EXPLAIN (ANALYZE, BUFFERS) output of each statement. Run it with
 * ./inspect (or .\inspect on Windows). ./inspect --help lists the flags.
 */
public final class Inspect {

    private static final String TABLE = """
            select c.relname as "table",
                   to_char(c.reltuples::bigint, 'FM999,999,999') as rows,
                   pg_size_pretty(pg_relation_size(c.oid)) as table_size,
                   pg_size_pretty(pg_indexes_size(c.oid)) as all_indexes_size
              from pg_class c
             where c.relname = 'orders' and c.relnamespace = 'public'::regnamespace
            """;

    private static final String INDEXES = """
            select c.relname as index,
                   substring(pg_get_indexdef(i.indexrelid) from 'USING btree (.*)$') as columns,
                   pg_size_pretty(pg_relation_size(i.indexrelid)) as size,
                   coalesce(s.idx_scan, 0) as scans,
                   case when i.indisprimary then 'primary key' else '' end as kind
              from pg_index i
              join pg_class c on c.oid = i.indexrelid
              join pg_class t on t.oid = i.indrelid
              left join pg_stat_user_indexes s on s.indexrelid = i.indexrelid
             where t.relname = 'orders' and t.relnamespace = 'public'::regnamespace
             order by i.indisprimary desc, c.relname
            """;

    private record Options(String mode, int depth, int size, long customer) {
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
        try (App app = App.start(false)) {
            if (app == null) {
                return;
            }
            System.out.println("TABLE (rows = the planner's estimate, refreshed by ANALYZE)");
            printTable(app.connection(), TABLE);
            System.out.println();
            System.out.println("INDEXES ON orders (scans = how many times a query used the index since it was built)");
            printTable(app.connection(), INDEXES);
            System.out.println();
            request(app, options);
            System.out.println();
        }
    }

    private static void request(App app, Options options) throws Exception {
        int size = options.size();
        long ordersBefore = (long) (options.depth() - 1) * size;
        String path = switch (options.mode()) {
            case "keyset" -> app.feedPathAfter(Math.min(ordersBefore, app.orders() - 1), size);
            case "history" -> "/api/customers/" + options.customer() + "/orders?page=" + (options.depth() - 1) + "&size=" + size;
            default -> "/api/orders?page=" + (options.depth() - 1) + "&size=" + size;
        };
        App.Response response = app.get(path);

        System.out.println("REQUEST (page " + number(options.depth()) + ", " + size + " orders per page)");
        System.out.println("  GET " + path);
        if (!response.ok()) {
            System.out.println("  " + response.status() + " " + response.error());
            System.out.println("  SQL statements sent: " + response.statements().size());
            return;
        }
        System.out.println("  200 OK, " + response.ids().size() + " orders returned, hasNext = " + response.hasNext());
        System.out.println("  SQL statements sent: " + response.statements().size());

        int position = 1;
        for (SqlStatement statement : response.statements()) {
            System.out.println();
            System.out.println("STATEMENT " + position++ + " of " + response.statements().size());
            statement.sql().strip().lines().forEach(line -> System.out.println("    " + line.strip()));
            if (!statement.bindings().isEmpty()) {
                System.out.println("    -- ? = " + App.values(statement));
            }
            System.out.println();
            System.out.println("  EXPLAIN (ANALYZE, BUFFERS)");
            app.planText(statement).forEach(line -> System.out.println("    " + line));
            Plan plan = app.plan(statement);
            System.out.println();
            System.out.println("  How the rows were found:  " + plan.describe());
            System.out.println("  Rows read:                " + number(plan.rowsRead()));
            System.out.println("  Rows returned:            " + number(plan.rowsReturned()));
            System.out.println("  Pages read (8 kB each):   " + number(plan.pages()));
            System.out.println("  Sorted by Postgres:       " + (plan.has("Sort") ? "yes, there is a Sort step" : "no"));
        }
    }

    /** Prints a query result as a plain text table. */
    private static void printTable(Connection connection, String sql) throws SQLException {
        List<String[]> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            int columns = result.getMetaData().getColumnCount();
            String[] header = new String[columns];
            for (int c = 0; c < columns; c++) {
                header[c] = result.getMetaData().getColumnLabel(c + 1);
            }
            rows.add(header);
            while (result.next()) {
                String[] row = new String[columns];
                for (int c = 0; c < columns; c++) {
                    String value = result.getString(c + 1);
                    row[c] = value == null ? "" : value;
                }
                rows.add(row);
            }
        }
        int[] widths = new int[rows.get(0).length];
        for (String[] row : rows) {
            for (int c = 0; c < row.length; c++) {
                widths[c] = Math.max(widths[c], row[c].length());
            }
        }
        for (int r = 0; r < rows.size(); r++) {
            StringBuilder line = new StringBuilder("  ");
            for (int c = 0; c < widths.length; c++) {
                line.append(String.format("%-" + widths[c] + "s", rows.get(r)[c])).append("  ");
            }
            System.out.println(line.toString().stripTrailing());
            if (r == 0) {
                StringBuilder rule = new StringBuilder("  ");
                for (int width : widths) {
                    rule.append("-".repeat(width)).append("  ");
                }
                System.out.println(rule.toString().stripTrailing());
            }
        }
    }

    private static Options parse(String[] args) {
        String mode = "offset";
        int depth = 5_000;
        int size = 20;
        long customer = 42;
        boolean depthGiven = false;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--help", "-h" -> {
                    return null;
                }
                case "--mode" -> {
                    mode = value(args, ++i, arg).toLowerCase(Locale.ROOT);
                    if (!List.of("offset", "keyset", "history").contains(mode)) {
                        throw new IllegalArgumentException("--mode must be offset, keyset or history, got: " + mode);
                    }
                }
                case "--depth" -> {
                    depth = between(value(args, ++i, arg), arg, 1, 1_000_000);
                    depthGiven = true;
                }
                case "--size" -> size = between(value(args, ++i, arg), arg, 1, 1_000_000);
                case "--customer" -> customer = between(value(args, ++i, arg), arg, 1, 100_000);
                default -> throw new IllegalArgumentException("Unknown option: " + arg);
            }
        }
        if (mode.equals("history") && !depthGiven) {
            depth = 1;
        }
        return new Options(mode, depth, size, customer);
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
        System.out.println("Usage: ./inspect [options]     (Windows: .\\inspect [options])");
        System.out.println();
        System.out.println("  --mode <name>     offset | keyset | history (default: offset)");
        System.out.println("  --depth <n>       which page to ask for (default: 5000; for history: 1)");
        System.out.println("  --size <n>        orders per page (default: 20)");
        System.out.println("  --customer <id>   the customer for --mode history (default: 42)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  ./inspect");
        System.out.println("  ./inspect --depth 1");
        System.out.println("  ./inspect --mode keyset --depth 5000");
        System.out.println("  ./inspect --mode history");
    }

    private Inspect() {
    }
}
