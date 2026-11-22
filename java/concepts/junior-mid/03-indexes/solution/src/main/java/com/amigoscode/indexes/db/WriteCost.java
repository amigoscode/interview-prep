package com.amigoscode.indexes.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * The price of an index is paid on every write. This experiment copies the same rows into two
 * scratch tables, one with no index and one with six, and measures both. The scratch tables are
 * dropped at the end: the orders table is only read.
 */
public final class WriteCost {

    /** The six indexes of the indexed scratch table. */
    public static final List<String> INDEXES = List.of(
            "CREATE UNIQUE INDEX scratch_pk ON scratch_indexed (id)",
            "CREATE INDEX scratch_tracking ON scratch_indexed (tracking_number)",
            "CREATE INDEX scratch_customer_created ON scratch_indexed (customer_id, created_at)",
            "CREATE INDEX scratch_created_customer ON scratch_indexed (created_at, customer_id)",
            "CREATE INDEX scratch_status ON scratch_indexed (status)",
            "CREATE INDEX scratch_total ON scratch_indexed (total_cents)");

    /**
     * @param rows          rows inserted into each table
     * @param bareMillis    time to insert them with no index
     * @param indexedMillis time to insert them with six indexes
     * @param tableBytes    size of the rows themselves (the same in both tables)
     * @param bareIndexBytes    index size of the table with no index: 0
     * @param indexedIndexBytes size of the six indexes together
     */
    public record Result(long rows, int indexes, long bareMillis, long indexedMillis, long tableBytes,
                         long bareIndexBytes, long indexedIndexBytes) {
    }

    public static Result run(Connection connection, long rows) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS scratch_bare, scratch_indexed");
            try {
                statement.execute("CREATE TABLE scratch_bare (LIKE orders)");
                statement.execute("CREATE TABLE scratch_indexed (LIKE orders)");
                for (String index : INDEXES) {
                    statement.execute(index);
                }
                // Read the source rows once first, so neither insert pays for a cold cache.
                statement.execute("SELECT count(tracking_number) FROM orders WHERE id <= " + rows);

                long bareMillis = insert(statement, "scratch_bare", rows);
                long indexedMillis = insert(statement, "scratch_indexed", rows);

                return new Result(count(statement, "scratch_indexed"), INDEXES.size(), bareMillis, indexedMillis,
                        number(statement, "SELECT pg_relation_size('scratch_indexed')"),
                        number(statement, "SELECT pg_indexes_size('scratch_bare')"),
                        number(statement, "SELECT pg_indexes_size('scratch_indexed')"));
            } finally {
                statement.execute("DROP TABLE IF EXISTS scratch_bare, scratch_indexed");
            }
        }
    }

    private static long insert(Statement statement, String table, long rows) throws SQLException {
        long start = System.nanoTime();
        statement.execute("INSERT INTO " + table + " SELECT * FROM orders WHERE id <= " + rows);
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static long count(Statement statement, String table) throws SQLException {
        return number(statement, "SELECT count(*) FROM " + table);
    }

    private static long number(Statement statement, String sql) throws SQLException {
        try (ResultSet row = statement.executeQuery(sql)) {
            row.next();
            return row.getLong(1);
        }
    }

    private WriteCost() {
    }
}
