package com.amigoscode.pagination.db;

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
 * 1. schema.sql creates the table if it is missing.
 * 2. If the table was not seeded with the wanted number of rows, it is emptied and seeded again
 *    with generate_series: the rows are made inside Postgres, which takes seconds, not minutes.
 * 3. If indexes.sql changed since the last run, every index except the primary key is dropped and
 *    indexes.sql is run again. Then VACUUM ANALYZE refreshes the planner's statistics.
 *
 * When nothing changed, steps 2 and 3 are skipped and this takes a few milliseconds.
 */
public final class DatabaseSetup {

    public static final long DEFAULT_ORDERS = 1_000_000;

    /** Orders that arrive together and share one created_at. */
    public static final int BATCH = 40;

    /** A problem the student can fix, for example a typo in indexes.sql. */
    public static final class SetupException extends RuntimeException {
        public SetupException(String message) {
            super(message);
        }
    }

    public static void run(Connection connection, long orders, Consumer<String> log) throws SQLException {
        if (orders < 1_000 || orders % BATCH != 0) {
            throw new IllegalArgumentException("Seed at least 1,000 orders, in a multiple of " + BATCH + ", got " + orders);
        }
        connection.setAutoCommit(true);
        try (Statement statement = connection.createStatement()) {
            // Only one setup at a time, even if the application and ./demo start together.
            statement.execute("select pg_advisory_lock(20261125)");
            try {
                for (String sql : SqlScript.statements(SqlScript.read("schema.sql"))) {
                    statement.execute(sql);
                }
                boolean seeded = seed(connection, statement, orders, log);
                applyIndexes(connection, statement, seeded, log);
            } finally {
                statement.execute("select pg_advisory_unlock(20261125)");
            }
        }
    }

    /** Forgets what was seeded, so the next run starts from an empty table. */
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
        log.accept(String.format(Locale.US, "Seeding %,d orders (first run only)...", orders));
        long start = System.nanoTime();

        statement.execute("delete from setup_state");
        statement.execute("truncate table orders");
        dropSecondaryIndexes(connection, statement);

        // The shop sells through a marketplace, which sends its orders in batches: 40 orders every
        // 40 minutes, written in one transaction, so all 40 share the same created_at.
        // - The first 12 orders of a batch belong to one business customer (ids 1 to 1,000, taking
        //   turns), so a business customer has many orders with the same created_at.
        // - The other 28 belong to retail customers (ids from 1,001).
        // - Only the newest orders are still PENDING. Of the rest, one in twenty was CANCELLED.
        statement.execute("""
                insert into orders (id, customer_id, status, total_cents, created_at)
                select i,
                       case when (i - 1) %% %2$d < 12 then 1 + ((i - 1) / %2$d) %% 1000
                            else 1001 + (i * 7919) %% 99000 end,
                       case when i > %1$d - %1$d / 250 then 'PENDING'
                            when i %% 20 = 0 then 'CANCELLED'
                            else 'DELIVERED' end,
                       500 + (i * 7919) %% 50000,
                       timestamptz '2024-01-01 00:00:00+00' + make_interval(mins => (((i - 1) / %2$d) * 40)::int)
                  from generate_series(1::bigint, %1$d) as i
                """.formatted(orders, BATCH));

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
        // ANALYZE collects the statistics the planner uses to choose a plan.
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
