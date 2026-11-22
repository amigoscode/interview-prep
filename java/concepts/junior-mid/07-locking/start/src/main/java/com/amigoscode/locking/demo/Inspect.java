package com.amigoscode.locking.demo;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows what the database sees right now: the product row, the number of orders, and every session
 * that is inside a transaction, including which session it is waiting for. Run it with ./inspect
 * (or .\inspect on Windows) in a second terminal while ./demo --pause is running.
 */
public final class Inspect {

    private static final String SESSIONS = """
            select a.pid,
                   a.state,
                   coalesce(a.wait_event_type || ': ' || a.wait_event, '') as waiting_on,
                   case when cardinality(pg_blocking_pids(a.pid)) = 0 then ''
                        else array_to_string(pg_blocking_pids(a.pid), ', ') end as blocked_by_pid,
                   coalesce(round(extract(epoch from (now() - a.xact_start)) * 1000)::text, '') as in_transaction_ms,
                   left(regexp_replace(a.query, '\\s+', ' ', 'g'), 78) as last_statement
              from pg_stat_activity a
             where a.datname = current_database()
               and a.pid <> pg_backend_pid()
               and a.backend_type = 'client backend'
               and a.state <> 'idle'
             order by a.xact_start nulls last, a.pid
            """;

    public static void main(String[] args) throws Exception {
        boolean watch = false;
        for (String arg : args) {
            if (arg.equals("--watch")) {
                watch = true;
            } else {
                System.out.println("Usage: ./inspect [--watch]     (Windows: .\\inspect [--watch])");
                System.out.println();
                System.out.println("  --watch   refresh every second for one minute (Ctrl+C to stop)");
                return;
            }
        }
        if (!Db.isUp()) {
            return;
        }
        try (Connection connection = Db.connect()) {
            int rounds = watch ? 60 : 1;
            for (int round = 0; round < rounds; round++) {
                snapshot(connection);
                if (round < rounds - 1) {
                    Thread.sleep(1000);
                }
            }
        }
    }

    private static void snapshot(Connection connection) throws SQLException {
        System.out.println();
        System.out.println("==== " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")) + " ====");
        System.out.println();
        System.out.println("PRODUCT");
        printTable(connection, "select id, name, stock, version from product order by id");
        System.out.println();
        System.out.println("ORDERS");
        printTable(connection, "select count(*) as order_rows, coalesce(sum(quantity), 0) as units_sold from orders");
        System.out.println();
        System.out.println("SESSIONS INSIDE A TRANSACTION (blocked_by_pid = the session this one is waiting for)");
        int sessions = printTable(connection, SESSIONS);
        if (sessions == 0) {
            System.out.println("  none. Nothing is running. Start ./demo --strategy pessimistic --pause in another terminal.");
        }
    }

    /** Prints a query result as a plain text table and returns the number of rows. */
    private static int printTable(Connection connection, String sql) throws SQLException {
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
        if (rows.size() == 1) {
            return 0;
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
        return rows.size() - 1;
    }

    private Inspect() {
    }
}
