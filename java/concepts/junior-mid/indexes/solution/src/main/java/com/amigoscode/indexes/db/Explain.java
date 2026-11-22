package com.amigoscode.indexes.db;

import com.amigoscode.indexes.shop.Query;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Asks Postgres how it runs a query. EXPLAIN ANALYZE really executes the query and reports what
 * happened: which scan, how many rows, how long.
 *
 * Parallel query is switched off for the EXPLAIN, so a sequential scan shows up as one plain
 * "Seq Scan" line instead of "Gather" over "Parallel Seq Scan" with per-worker numbers.
 */
public final class Explain {

    /** The plan as an object you can ask questions. */
    public static Plan analyze(Connection connection, Query query) throws SQLException {
        return Plan.parse(String.join("\n", run(connection, "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) ", query)));
    }

    /** The plan as the text Postgres prints, one line per list entry. */
    public static List<String> text(Connection connection, Query query, boolean buffers) throws SQLException {
        return run(connection, buffers ? "EXPLAIN (ANALYZE, BUFFERS) " : "EXPLAIN (ANALYZE, BUFFERS OFF) ", query);
    }

    private static List<String> run(Connection connection, String explain, Query query) throws SQLException {
        try (Statement settings = connection.createStatement()) {
            settings.execute("SET max_parallel_workers_per_gather = 0");
            try (PreparedStatement statement = connection.prepareStatement(explain + query.sql())) {
                for (int i = 0; i < query.args().size(); i++) {
                    statement.setObject(i + 1, query.args().get(i));
                }
                List<String> lines = new ArrayList<>();
                try (ResultSet row = statement.executeQuery()) {
                    while (row.next()) {
                        lines.add(row.getString(1));
                    }
                }
                return lines;
            } finally {
                try {
                    settings.execute("RESET max_parallel_workers_per_gather");
                } catch (SQLException ignored) {
                    // The transaction is already broken by the real error, which is the one to report.
                }
            }
        }
    }

    private Explain() {
    }
}
