package com.amigoscode.indexes.demo;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows what the database knows about the shop's tables: how big they are, which indexes they have,
 * how big each index is, how often it was used, and what the planner believes about the status
 * column. Run it with ./inspect (or .\inspect on Windows).
 */
public final class Inspect {

    private static final String TABLES = """
            select c.relname as "table",
                   to_char(c.reltuples::bigint, 'FM999,999,999') as rows,
                   pg_size_pretty(pg_relation_size(c.oid)) as table_size,
                   pg_size_pretty(pg_indexes_size(c.oid)) as all_indexes_size,
                   (select count(*) from pg_index i where i.indrelid = c.oid) as indexes,
                   c.relallvisible || ' of ' || c.relpages as pages_all_visible
              from pg_class c
             where c.relname in ('orders', 'customers')
               and c.relnamespace = 'public'::regnamespace
             order by c.relname desc
            """;

    private static final String INDEXES = """
            select t.relname as "table",
                   c.relname as index,
                   replace(substring(pg_get_indexdef(i.indexrelid) from ' USING btree (.*)$'), '::text', '') as columns,
                   pg_size_pretty(pg_relation_size(i.indexrelid)) as size,
                   coalesce(s.idx_scan, 0) as scans,
                   case when i.indisprimary then 'primary key'
                        when i.indpred is not null then 'partial'
                        when i.indexprs is not null then 'expression'
                        when i.indnkeyatts < i.indnatts then 'covering'
                        else '' end as kind
              from pg_index i
              join pg_class c on c.oid = i.indexrelid
              join pg_class t on t.oid = i.indrelid
              left join pg_stat_user_indexes s on s.indexrelid = i.indexrelid
             where t.relname in ('orders', 'customers')
               and t.relnamespace = 'public'::regnamespace
             order by t.relname desc, i.indisprimary desc, c.relname
            """;

    private static final String STATUS = """
            select v.status,
                   to_char(100 * v.share, 'FM990.0') || '%' as share_of_rows,
                   to_char((v.share * c.reltuples)::bigint, 'FM999,999,999') as estimated_rows
              from pg_stats s
              join pg_class c on c.relname = s.tablename and c.relnamespace = 'public'::regnamespace
             cross join lateral unnest(s.most_common_vals::text::text[], s.most_common_freqs) as v(status, share)
             where s.schemaname = 'public' and s.tablename = 'orders' and s.attname = 'status'
             order by v.share desc
            """;

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            System.out.println("Usage: ./inspect     (Windows: .\\inspect)");
            System.out.println();
            System.out.println("Shows the tables, every index with its size and how often it was used,");
            System.out.println("and what the planner knows about the status column. It takes no options.");
            return;
        }
        if (!Db.isUp()) {
            return;
        }
        try (Connection connection = Db.connect()) {
            if (!tablesExist(connection)) {
                System.out.println();
                System.out.println("The tables are not there yet. Run ./demo once: it creates and seeds them.");
                System.out.println();
                return;
            }
            System.out.println();
            System.out.println("TABLES (rows = the planner's estimate, refreshed by ANALYZE)");
            printTable(connection, TABLES);
            System.out.println();
            System.out.println("INDEXES (scans = how many times a query used the index since it was built)");
            printTable(connection, INDEXES);
            System.out.println();
            System.out.println("WHAT THE PLANNER KNOWS ABOUT orders.status (from pg_stats, a sample taken by ANALYZE)");
            printTable(connection, STATUS);
            System.out.println();
        }
    }

    private static boolean tablesExist(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery("select to_regclass('public.orders') is not null")) {
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
