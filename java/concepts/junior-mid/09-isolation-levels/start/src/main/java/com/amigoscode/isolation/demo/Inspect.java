package com.amigoscode.isolation.demo;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows what the database sees right now: every account row with the transaction that wrote it,
 * every session that is inside a transaction, whether it holds a snapshot, and the predicate locks
 * of SERIALIZABLE sessions. Run it with ./inspect (or .\inspect on Windows) in a second terminal
 * while ./demo --pause is running.
 */
public final class Inspect {

    private static final String ACCOUNTS = """
            select id, name, balance, xmin, xmax
              from account
             order by id
            """;

    /**
     * Postgres has no column that shows another session's isolation level. The demo's sessions put
     * their own level into their application_name, which is the "session" column here.
     * snapshot_xmin is filled in while a session holds a snapshot.
     */
    private static final String SESSIONS = """
            select a.pid,
                   a.application_name as session,
                   a.state,
                   coalesce(a.backend_xmin::text, '') as snapshot_xmin,
                   coalesce(a.backend_xid::text, '') as own_xid,
                   case when cardinality(pg_blocking_pids(a.pid)) = 0 then ''
                        else array_to_string(pg_blocking_pids(a.pid), ', ') end as waits_for_pid,
                   coalesce(round(extract(epoch from (now() - a.xact_start)) * 1000)::text, '') as in_transaction_ms,
                   left(regexp_replace(a.query, '\\s+', ' ', 'g'), 44) as last_statement
              from pg_stat_activity a
             where a.datname = current_database()
               and a.pid <> pg_backend_pid()
               and a.backend_type = 'client backend'
               and a.state <> 'idle'
             order by a.xact_start nulls last, a.pid
            """;

    private static final String PREDICATE_LOCKS = """
            select l.pid,
                   a.application_name as session,
                   coalesce(c.relname::text, '') as relation,
                   l.locktype as covers,
                   coalesce(l.page::text, '') as page,
                   coalesce(l.tuple::text, '') as row_on_page
              from pg_locks l
              join pg_stat_activity a on a.pid = l.pid
              left join pg_class c on c.oid = l.relation
             where l.mode = 'SIReadLock'
             order by l.pid, c.relname, l.page nulls first, l.tuple nulls first
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
        System.out.println("ACCOUNTS (what a NEW transaction would read. xmin = the transaction that wrote this version of");
        System.out.println("          the row, xmax = a transaction that has locked or replaced it, 0 = none)");
        if (!tableExists(connection)) {
            System.out.println("  The account table does not exist yet. Run ./demo once: it creates the table.");
            return;
        }
        printTable(connection, ACCOUNTS);
        System.out.println();
        System.out.println("SESSIONS INSIDE A TRANSACTION (snapshot_xmin is filled in while the session holds a snapshot)");
        int sessions = printTable(connection, SESSIONS);
        if (sessions == 0) {
            System.out.println("  none. Nothing is running. Start ./demo --pause in another terminal.");
        }
        System.out.println();
        System.out.println("PREDICATE LOCKS (SIReadLock: what SERIALIZABLE sessions have read. They block nobody)");
        int locks = printTable(connection, PREDICATE_LOCKS);
        if (locks == 0) {
            System.out.println("  none. No SERIALIZABLE transaction is open.");
        }
    }

    private static boolean tableExists(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery("select to_regclass('account') is not null")) {
            row.next();
            return row.getBoolean(1);
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
