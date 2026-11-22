package com.amigoscode.sqltraps.db;

import com.amigoscode.sqltraps.shop.Query;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Asks Postgres how it runs a query. EXPLAIN ANALYZE really executes the query and reports what
 * happened: which join, which scan, how many rows it expected and how many it got, how long.
 *
 * Two settings are switched off for the EXPLAIN, so the plans are the same on every machine:
 * parallel query (a scan shows up as one plain "Seq Scan" line instead of "Gather" over
 * "Parallel Seq Scan") and JIT compilation (which adds a "JIT:" block to expensive plans).
 */
public final class Explain {

    /** The plan as an object you can ask questions. The query is executed. */
    public static Plan analyze(Connection connection, Query query) throws SQLException {
        return Plan.parse(String.join("\n", run(connection, "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) ", query)));
    }

    /** The plan as the text Postgres prints, one line per list entry. The query is executed. */
    public static List<String> text(Connection connection, Query query, boolean buffers) throws SQLException {
        return run(connection, buffers ? "EXPLAIN (ANALYZE, BUFFERS) " : "EXPLAIN (ANALYZE, BUFFERS OFF) ", query);
    }

    /** How many rows the planner expects the query to return. The query is not executed. */
    public static long estimatedRows(Connection connection, Query query) throws SQLException {
        return Plan.parse(String.join("\n", run(connection, "EXPLAIN (FORMAT JSON) ", query))).nodes().getFirst().estimatedRows();
    }

    private static List<String> run(Connection connection, String explain, Query query) throws SQLException {
        // SET LOCAL inside a transaction, plain SET outside one: both end with the EXPLAIN.
        boolean inTransaction = !connection.getAutoCommit();
        String set = inTransaction ? "SET LOCAL " : "SET ";
        try (Statement settings = connection.createStatement()) {
            settings.execute(set + "max_parallel_workers_per_gather = 0");
            settings.execute(set + "jit = off");
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
                if (!inTransaction) {
                    try {
                        settings.execute("RESET max_parallel_workers_per_gather");
                        settings.execute("RESET jit");
                    } catch (SQLException ignored) {
                        // The real error is the one to report.
                    }
                }
            }
        }
    }

    private Explain() {
    }
}
