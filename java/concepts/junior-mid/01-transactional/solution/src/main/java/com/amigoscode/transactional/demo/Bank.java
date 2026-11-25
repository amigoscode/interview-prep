package com.amigoscode.transactional.demo;

import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The four accounts every demo run and every test starts from, and plain SQL helpers to look at
 * them. Plain SQL on purpose: it reads what is really committed in the database, with no
 * Hibernate cache in between.
 */
public final class Bank {

    public static final long ALICE = 1;
    public static final long BOB = 2;
    /** Carol's account is frozen: any deposit or withdrawal fails. */
    public static final long CAROL = 3;
    public static final long ACME = 4;

    private Bank() {
    }

    /** Alice 100, Bob 0, Carol 0 (frozen), Acme Ltd 1000, and an empty audit log. */
    public static void reset(JdbcTemplate jdbc) {
        jdbc.execute("truncate table audit_log restart identity");
        jdbc.execute("delete from accounts");
        jdbc.update("""
                insert into accounts (id, owner, balance, frozen)
                values (1, 'Alice', 100, false),
                       (2, 'Bob', 0, false),
                       (3, 'Carol', 0, true),
                       (4, 'Acme Ltd', 1000, false)
                """);
    }

    public static long balance(JdbcTemplate jdbc, long accountId) {
        return jdbc.queryForObject("select balance from accounts where id = ?", Long.class, accountId);
    }

    public static String owner(JdbcTemplate jdbc, long accountId) {
        return jdbc.queryForObject("select owner from accounts where id = ?", String.class, accountId);
    }

    /** All the money in the bank. A transfer must never change it. */
    public static long total(JdbcTemplate jdbc) {
        return jdbc.queryForObject("select coalesce(sum(balance), 0) from accounts", Long.class);
    }

    public static int auditRows(JdbcTemplate jdbc) {
        return jdbc.queryForObject("select count(*) from audit_log", Integer.class);
    }

    static void printAccounts(JdbcTemplate jdbc) {
        printTable(jdbc, "select id, owner, balance, case when frozen then 'yes' else '' end as frozen from accounts order by id");
    }

    static void printAuditLog(JdbcTemplate jdbc) {
        if (printTable(jdbc, "select id, action, detail from audit_log order by id") == 0) {
            System.out.println("  (no rows)");
        }
    }

    /** Prints a query result as a plain text table and returns the number of rows. */
    static int printTable(JdbcTemplate jdbc, String sql) {
        List<String[]> rows = new ArrayList<>();
        jdbc.query(sql, result -> {
            int columns = result.getMetaData().getColumnCount();
            if (rows.isEmpty()) {
                String[] header = new String[columns];
                for (int c = 0; c < columns; c++) {
                    header[c] = result.getMetaData().getColumnLabel(c + 1);
                }
                rows.add(header);
            }
            String[] row = new String[columns];
            for (int c = 0; c < columns; c++) {
                String value = result.getString(c + 1);
                row[c] = value == null ? "" : value;
            }
            rows.add(row);
        });
        if (rows.isEmpty()) {
            return 0;
        }
        printRows(rows);
        return rows.size() - 1;
    }

    /** Prints rows as a table. The first row is the header. */
    static void printRows(List<String[]> rows) {
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
}
