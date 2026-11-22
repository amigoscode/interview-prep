package com.amigoscode.sqltraps.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Puts the database in a known state. There are two data sets:
 *
 * SMALL: 8 customers and 12 orders, every row written out by hand in seed-small.sql. The wrong-result
 *        half of the project runs on it, because you can check every answer by reading the rows.
 * BIG:   the small shop plus 20,000 customers, 300,000 orders and 600,000 order lines, generated
 *        inside Postgres by seed-big.sql. The slow-query half runs on it.
 *
 * ensure() is safe to call as often as you like: when the wanted data set is already loaded it does
 * nothing. Otherwise it empties the tables, loads the rows and runs ANALYZE, so the planner's
 * statistics describe the rows that are really there.
 */
public final class DatabaseSetup {

    public enum Shop {
        SMALL, BIG;

        public static Shop from(String name) {
            return valueOf(name.strip().toUpperCase(Locale.ROOT));
        }
    }

    /** The four tables, children first. */
    public static final List<String> TABLES = List.of("order_items", "orders", "customers", "products");

    private static final String STALE = "BIG, WITH STALE STATISTICS";

    public static void ensure(Connection connection, Shop shop, Consumer<String> log) throws SQLException {
        locked(connection, statement -> {
            if (shop.name().equals(state(connection))) {
                return;
            }
            long start = System.nanoTime();
            if (shop == Shop.BIG) {
                log.accept("Loading the big shop: 300,012 orders, 600,018 order lines, 20,008 customers...");
            }
            empty(statement);
            run(statement, "seed-small.sql");
            if (shop == Shop.BIG) {
                bulkLoad(statement);
            }
            analyze(statement);
            // Read every row once, so the first query you look at does not pay for a cold cache.
            statement.execute("select count(*) from orders");
            statement.execute("select count(*) from order_items");
            setState(connection, shop.name());
            if (shop == Shop.BIG) {
                log.accept(String.format(Locale.US, "Loaded and analyzed in %.1f s.", (System.nanoTime() - start) / 1e9));
            }
        });
    }

    /**
     * The "nobody ran ANALYZE" state: the small shop is loaded and analyzed, then the 300,000 orders
     * are loaded on top and ANALYZE is NOT run. The planner's statistics still describe 12 orders.
     * Autovacuum would normally repair this within a minute or so. It is switched off for these
     * tables (see schema.sql), so the state lasts until analyzeNow() is called.
     */
    public static void loadBigWithStaleStatistics(Connection connection, Consumer<String> log) throws SQLException {
        locked(connection, statement -> {
            long start = System.nanoTime();
            empty(statement);
            run(statement, "seed-small.sql");
            analyze(statement);
            log.accept("Loaded the small shop (12 orders) and ran ANALYZE.");
            bulkLoad(statement);
            statement.execute("select count(*) from orders");
            statement.execute("select pg_stat_force_next_flush()");
            setState(connection, STALE);
            log.accept(String.format(Locale.US,
                    "Loaded 300,000 more orders in %.1f s. ANALYZE has NOT run since.", (System.nanoTime() - start) / 1e9));
        });
    }

    /** Runs ANALYZE on the four tables. After stale statistics, this makes the shop a normal BIG shop. */
    public static void analyzeNow(Connection connection) throws SQLException {
        locked(connection, statement -> {
            analyze(statement);
            if (STALE.equals(state(connection))) {
                setState(connection, Shop.BIG.name());
            }
        });
    }

    /** "SMALL", "BIG", "BIG, WITH STALE STATISTICS", or null when nothing is loaded yet. */
    public static String state(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet exists = statement.executeQuery("select to_regclass('public.setup_state') is not null")) {
            exists.next();
            if (!exists.getBoolean(1)) {
                return null;
            }
        }
        try (PreparedStatement select = connection.prepareStatement("select value from setup_state where key = 'shop'");
             ResultSet row = select.executeQuery()) {
            return row.next() ? row.getString(1) : null;
        }
    }

    private interface Work {
        void run(Statement statement) throws SQLException;
    }

    /** Only one setup at a time, even if the application, ./demo and ./check start together. */
    private static void locked(Connection connection, Work work) throws SQLException {
        connection.setAutoCommit(true);
        try (Statement statement = connection.createStatement()) {
            statement.execute("select pg_advisory_lock(20261122)");
            try {
                run(statement, "schema.sql");
                work.run(statement);
            } finally {
                statement.execute("select pg_advisory_unlock(20261122)");
            }
        }
    }

    private static void empty(Statement statement) throws SQLException {
        statement.execute("delete from setup_state");
        statement.execute("truncate table " + String.join(", ", TABLES));
    }

    private static void analyze(Statement statement) throws SQLException {
        // Report this session's row counters first, so "rows changed since the last ANALYZE" starts at 0.
        statement.execute("select pg_stat_force_next_flush()");
        for (String table : TABLES) {
            statement.execute("analyze " + table);
        }
    }

    /**
     * Runs seed-big.sql with the foreign key checks switched off for this session. Checking 1.5
     * million references one by one takes 15 seconds, and the formulas in the script cannot
     * produce a key that does not exist.
     */
    private static void bulkLoad(Statement statement) throws SQLException {
        statement.execute("set session_replication_role = replica");
        try {
            run(statement, "seed-big.sql");
        } finally {
            statement.execute("set session_replication_role = origin");
        }
    }

    private static void run(Statement statement, String resource) throws SQLException {
        for (String sql : SqlScript.statements(SqlScript.read(resource))) {
            statement.execute(sql);
        }
    }

    private static void setState(Connection connection, String value) throws SQLException {
        try (PreparedStatement upsert = connection.prepareStatement("""
                insert into setup_state (key, value) values ('shop', ?)
                on conflict (key) do update set value = excluded.value
                """)) {
            upsert.setString(1, value);
            upsert.executeUpdate();
        }
    }

    private DatabaseSetup() {
    }
}
