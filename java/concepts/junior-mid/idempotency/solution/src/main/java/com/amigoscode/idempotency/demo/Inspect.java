package com.amigoscode.idempotency.demo;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows what the database sees right now: each customer's balance and what they were charged, the
 * payment rows, the idempotency-key rows with the stored answer, and every session that is inside
 * a transaction, including which session it is waiting for. Run it with ./inspect (or .\inspect on
 * Windows) after a ./demo, or in a second terminal while ./demo --pause is running.
 */
public final class Inspect {

    private static final String CUSTOMERS = """
            select c.id,
                   c.name,
                   to_char(c.balance_cents / 100.0, 'FM999999990.00') as balance,
                   count(p.id) as payments,
                   to_char(coalesce(sum(p.amount_cents), 0) / 100.0, 'FM999999990.00') as total_charged
              from customer c
              left join payment p on p.customer_id = c.id
             group by c.id, c.name, c.balance_cents
             order by c.id
            """;

    private static final String PAYMENTS = """
            select id,
                   customer_id,
                   to_char(amount_cents / 100.0, 'FM999999990.00') as amount,
                   to_char(created_at, 'HH24:MI:SS.MS') as created_at
              from payment
             order by id
            """;

    private static final String KEYS = """
            select customer_id,
                   idempotency_key,
                   coalesce(left(request_hash, 12) || '...', '') as request_hash,
                   payment_id,
                   response_status,
                   response_body,
                   case when expires_at < now() then 'EXPIRED'
                        else to_char(expires_at - now(), 'HH24"h" MI"m"') end as expires_in
              from idempotency_key
             order by id
            """;

    private static final String SESSIONS = """
            select a.pid,
                   a.state,
                   coalesce(a.wait_event_type || ': ' || a.wait_event, '') as waiting_on,
                   case when cardinality(pg_blocking_pids(a.pid)) = 0 then ''
                        else array_to_string(pg_blocking_pids(a.pid), ', ') end as blocked_by_pid,
                   coalesce(round(extract(epoch from (now() - a.xact_start)) * 1000)::text, '') as in_transaction_ms,
                   left(regexp_replace(a.query, '\\s+', ' ', 'g'), 60) as last_statement
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
                try {
                    snapshot(connection);
                } catch (SQLException e) {
                    if (!"42P01".equals(e.getSQLState())) {
                        throw e;
                    }
                    // The application creates the tables when it starts. No table yet = it never ran.
                    System.out.println("  The tables do not exist yet. Run ./demo once first.");
                }
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
        System.out.println("CUSTOMERS");
        printTable(connection, CUSTOMERS);
        System.out.println();
        System.out.println("PAYMENTS");
        if (printTable(connection, PAYMENTS) == 0) {
            System.out.println("  none");
        }
        System.out.println();
        System.out.println("IDEMPOTENCY KEYS (one row per key the server remembers, with the answer it gave)");
        if (printTable(connection, KEYS) == 0) {
            System.out.println("  none. The server remembers no request.");
        }
        System.out.println();
        System.out.println("SESSIONS INSIDE A TRANSACTION (blocked_by_pid = the session this one is waiting for)");
        if (printTable(connection, SESSIONS) == 0) {
            System.out.println("  none. Nothing is running. Start ./demo --scenario double-click --pause in another terminal.");
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
