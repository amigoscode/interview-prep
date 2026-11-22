package com.amigoscode.sqltraps.demo;

import com.amigoscode.sqltraps.db.DatabaseSetup;
import com.amigoscode.sqltraps.db.Explain;
import com.amigoscode.sqltraps.db.PlanFacts;
import com.amigoscode.sqltraps.shop.Query;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * What is in the tables, next to what the planner believes is in them. The planner never counts
 * rows when it plans a query: it uses the statistics that the last ANALYZE wrote down.
 */
final class ShopState {

    private static final String FACTS = """
            select pg_size_pretty(pg_relation_size(c.oid)),
                   coalesce(to_char(greatest(s.last_analyze, s.last_autoanalyze), 'HH24:MI:SS') || ' ('
                            || round(extract(epoch from now() - greatest(s.last_analyze, s.last_autoanalyze))) || ' s ago)',
                            'never'),
                   s.n_mod_since_analyze
              from pg_class c
              join pg_stat_user_tables s on s.relid = c.oid
             where c.relname = ? and c.relnamespace = 'public'::regnamespace
            """;

    private static final String STATUS = """
            with believed as (
                select v.status, v.share
                  from pg_stats s
                 cross join lateral unnest(s.most_common_vals::text::text[], s.most_common_freqs) as v(status, share)
                 where s.schemaname = 'public' and s.tablename = 'orders' and s.attname = 'status'),
            counted as (
                select status, count(*) as n, count(*)::numeric / sum(count(*)) over () as share
                  from orders
                 group by status)
            select coalesce(r.status, b.status) as status,
                   coalesce(to_char(r.n, 'FM999,999,999'), '0') as rows_now,
                   coalesce(to_char(100 * r.share, 'FM990.0') || '%', '0.0%') as share_now,
                   coalesce(to_char(100 * b.share, 'FM990.0') || '%', 'does not know this value') as planner_thinks
              from counted r
              full join believed b on b.status = r.status
             order by r.n desc nulls last, 1
            """;

    static boolean tablesExist(Connection connection) throws SQLException {
        return DatabaseSetup.state(connection) != null;
    }

    static void printTables(Connection connection) throws SQLException {
        System.out.println("TABLES (rows_now = counted just now. planner_thinks = what the planner will assume, from the last ANALYZE)");
        List<List<String>> rows = new ArrayList<>();
        for (String table : List.of("customers", "orders", "order_items", "products")) {
            long counted = number(connection, "select count(*) from " + table);
            long believed = Explain.estimatedRows(connection, new Query("select * from " + table));
            try (PreparedStatement facts = connection.prepareStatement(FACTS)) {
                facts.setString(1, table);
                try (ResultSet row = facts.executeQuery()) {
                    row.next();
                    rows.add(List.of(table, PlanFacts.number(counted), PlanFacts.number(believed), row.getString(1),
                            row.getString(2), PlanFacts.number(row.getLong(3))));
                }
            }
        }
        new TextTable(List.of("table", "rows_now", "planner_thinks", "size", "last_analyze", "rows_changed_since"), rows).print();
    }

    static void printStatusStatistics(Connection connection) throws SQLException {
        System.out.println("orders.status (share_now = counted just now. planner_thinks = the share in the statistics)");
        try (PreparedStatement statement = connection.prepareStatement(STATUS)) {
            TextTable.read(statement, 50).print();
        }
    }

    static long number(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet row = statement.executeQuery()) {
            row.next();
            return row.getLong(1);
        }
    }

    private ShopState() {
    }
}
