package com.amigoscode.indexes.db;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Puts the database in a known state. Safe to run as often as you like:
 *
 * 1. schema.sql creates the tables if they are missing.
 * 2. If the tables do not hold the wanted number of rows, they are emptied and seeded again with
 *    generate_series: the rows are made inside Postgres, which takes seconds, not minutes.
 * 3. If indexes.sql changed since the last run, every index except the primary keys is dropped and
 *    indexes.sql is run again. Then VACUUM ANALYZE refreshes the planner's statistics.
 *
 * When nothing changed, steps 2 and 3 are skipped and this takes a few milliseconds.
 */
public final class DatabaseSetup {

    public static final long DEFAULT_ORDERS = 1_000_000;

    /** A problem the student can fix, for example a typo in indexes.sql. */
    public static final class SetupException extends RuntimeException {
        public SetupException(String message) {
            super(message);
        }
    }

    public static void run(Connection connection, long orders, Consumer<String> log) throws SQLException {
        if (orders < 10_000) {
            throw new IllegalArgumentException("Seed at least 10,000 orders, got " + orders);
        }
        connection.setAutoCommit(true);
        try (Statement statement = connection.createStatement()) {
            // Only one setup at a time, even if the application and ./demo start together.
            statement.execute("select pg_advisory_lock(20261120)");
            try {
                for (String sql : SqlScript.statements(SqlScript.read("schema.sql"))) {
                    statement.execute(sql);
                }
                boolean seeded = seed(connection, statement, orders, log);
                applyIndexes(connection, statement, seeded, log);
            } finally {
                statement.execute("select pg_advisory_unlock(20261120)");
            }
        }
    }

    /** Forgets what was seeded, so the next run starts from empty tables. */
    public static void forget(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("drop table if exists setup_state");
        }
    }

    private static boolean seed(Connection connection, Statement statement, long orders, Consumer<String> log)
            throws SQLException {
        if (String.valueOf(orders).equals(state(connection, "seeded_orders"))) {
            return false;
        }
        long customers = orders / 10;
        long pending = orders / 250;
        log.accept(String.format(Locale.US, "Seeding %,d orders for %,d customers (first run only)...", orders, customers));
        long start = System.nanoTime();

        statement.execute("delete from setup_state");
        statement.execute("truncate table orders, customers");
        dropSecondaryIndexes(connection, statement);

        // Emails are stored the way people typed them: every third one has capitals.
        statement.execute("""
                insert into customers (id, email, name)
                select i,
                       case when i %% 3 = 0 then 'Customer' || i || '@Shop.test'
                            else 'customer' || i || '@shop.test' end,
                       'Customer ' || i
                  from generate_series(1::bigint, %d) as i
                """.formatted(customers));

        // Skew, like a real shop:
        // - orders are created over two years, oldest first
        // - customers 1 to 10 are big accounts with 100 times more orders than anyone else
        // - only the newest orders are still PENDING (0.4%) or SHIPPED (1.6%). Of the rest, one in
        //   twenty was CANCELLED and all the others are DELIVERED
        statement.execute("""
                insert into orders (id, customer_id, status, total_cents, tracking_number, created_at)
                select i,
                       case when i %% 100 = 0 then 1 + (i / 100) %% 10
                            else 11 + (i * 7919) %% (%2$d - 10) end,
                       case when i > %1$d - %3$d then 'PENDING'
                            when i > %1$d - %3$d * 5 then 'SHIPPED'
                            when i %% 20 = 0 then 'CANCELLED'
                            else 'DELIVERED' end,
                       500 + (i * 7919) %% 50000,
                       'TRK' || upper(substr(md5(i::text), 1, 12)),
                       timestamp '2024-01-01 00:00:00' + make_interval(secs => (i - 1) * 63072000.0 / %1$d)
                  from generate_series(1::bigint, %1$d) as i
                """.formatted(orders, customers, pending));

        setState(connection, "seeded_orders", String.valueOf(orders));
        log.accept(String.format(Locale.US, "Seeded in %.1f s.", (System.nanoTime() - start) / 1e9));
        return true;
    }

    private static void applyIndexes(Connection connection, Statement statement, boolean seeded, Consumer<String> log)
            throws SQLException {
        String script = SqlScript.read("indexes.sql");
        List<String> statements = SqlScript.statements(script);
        String hash = sha256(String.join(";", statements));
        if (!seeded && hash.equals(state(connection, "indexes_sql"))) {
            return;
        }
        log.accept("indexes.sql changed: rebuilding " + statements.size()
                + (statements.size() == 1 ? " index..." : " indexes..."));
        long start = System.nanoTime();
        statement.execute("delete from setup_state where key = 'indexes_sql'");
        dropSecondaryIndexes(connection, statement);
        for (int i = 0; i < statements.size(); i++) {
            try {
                statement.execute(statements.get(i));
            } catch (SQLException e) {
                throw new SetupException("Statement " + (i + 1) + " in src/main/resources/indexes.sql failed:\n\n    "
                        + statements.get(i) + "\n\n" + e.getMessage().lines().findFirst().orElse(""));
            }
        }
        // VACUUM marks pages as "all visible" (index-only scans need that), ANALYZE collects the
        // statistics the planner uses to choose between an index and a sequential scan.
        statement.execute("vacuum (analyze) customers");
        statement.execute("vacuum (analyze) orders");
        setState(connection, "indexes_sql", hash);
        log.accept(String.format(Locale.US, "Indexes ready in %.1f s.", (System.nanoTime() - start) / 1e9));
    }

    private static void dropSecondaryIndexes(Connection connection, Statement statement) throws SQLException {
        for (IndexInfo index : IndexInfo.all(connection)) {
            if (index.secondary()) {
                statement.execute("drop index if exists \"" + index.name() + "\"");
            }
        }
    }

    private static String state(Connection connection, String key) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("select value from setup_state where key = ?")) {
            select.setString(1, key);
            try (ResultSet row = select.executeQuery()) {
                return row.next() ? row.getString(1) : null;
            }
        }
    }

    private static void setState(Connection connection, String key, String value) throws SQLException {
        try (PreparedStatement upsert = connection.prepareStatement("""
                insert into setup_state (key, value) values (?, ?)
                on conflict (key) do update set value = excluded.value
                """)) {
            upsert.setString(1, key);
            upsert.setString(2, value);
            upsert.executeUpdate();
        }
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private DatabaseSetup() {
    }
}
