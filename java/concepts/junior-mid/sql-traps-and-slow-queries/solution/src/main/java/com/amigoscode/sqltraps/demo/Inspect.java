package com.amigoscode.sqltraps.demo;

import com.amigoscode.sqltraps.db.DatabaseSetup;
import com.amigoscode.sqltraps.db.Explain;
import com.amigoscode.sqltraps.db.Plan;
import com.amigoscode.sqltraps.db.PlanFacts;
import com.amigoscode.sqltraps.db.ReadOnlySql;
import com.amigoscode.sqltraps.shop.Query;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Makes the invisible visible. ./inspect "SELECT ..." runs any read-only SELECT against the shop and
 * prints its first rows, the plan Postgres used (EXPLAIN ANALYZE) and the facts that matter pulled
 * out of that plan. ./inspect with no argument shows the tables and the planner's statistics.
 * On Windows: .\inspect "SELECT ..."
 */
public final class Inspect {

    private static final int PREVIEW_ROWS = 20;

    public static void main(String[] args) throws Exception {
        // The launcher passes the statement as one system property, so quotes inside it survive.
        String sql = System.getProperty("inspect.sql", String.join(" ", args)).strip();
        if (sql.equals("--help") || sql.equals("-h")) {
            printHelp();
            return;
        }
        if (!Db.isUp()) {
            return;
        }
        try (Connection connection = Db.connect()) {
            if (!ShopState.tablesExist(connection)) {
                System.out.println();
                System.out.println("The tables are not there yet. Run ./demo once: it creates and fills them.");
                System.out.println();
                return;
            }
            System.out.println();
            if (!sql.isEmpty()) {
                String statement;
                try {
                    statement = ReadOnlySql.check(sql);
                } catch (ReadOnlySql.NotReadOnly e) {
                    System.out.println("REFUSED. " + e.getMessage());
                    System.out.println();
                    System.out.println("./inspect only runs one read-only SELECT. Example:");
                    System.out.println("    ./inspect \"SELECT status, COUNT(*) FROM orders GROUP BY status\"");
                    System.out.println();
                    return;
                }
                if (!query(connection, statement)) {
                    return;
                }
            }
            System.out.println("The shop that is loaded: " + DatabaseSetup.state(connection));
            System.out.println();
            ShopState.printTables(connection);
            System.out.println();
            if (sql.isEmpty()) {
                ShopState.printStatusStatistics(connection);
                System.out.println();
                System.out.println("To look at a query: ./inspect \"SELECT ...\"     (./inspect --help)");
                System.out.println();
            }
        }
    }

    /** Runs the statement in a READ ONLY transaction: rows, plan, facts. Returns false if Postgres refused it. */
    private static boolean query(Connection connection, String sql) throws SQLException {
        Query query = new Query(sql);
        connection.setReadOnly(true);
        connection.setAutoCommit(false);
        try {
            try (Statement settings = connection.createStatement()) {
                settings.execute("SET LOCAL statement_timeout = '120s'");
            }
            TextTable preview;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setFetchSize(PREVIEW_ROWS);
                preview = TextTable.read(statement, PREVIEW_ROWS);
            }
            List<String> text = Explain.text(connection, query, false);
            Plan plan = Explain.analyze(connection, query);

            long total = plan.rowsReturned();
            System.out.println(total > PREVIEW_ROWS
                    ? "ROWS (the first " + PREVIEW_ROWS + " of " + PlanFacts.number(total) + ")"
                    : "ROWS (" + total + ")");
            preview.print();
            System.out.println();
            System.out.println("PLAN (EXPLAIN ANALYZE: the query really ran. Parallel query and JIT are switched off)");
            text.forEach(line -> System.out.println("  " + line));
            System.out.println();
            System.out.println("WHAT STANDS OUT");
            PlanFacts.lines(plan).forEach(line -> System.out.println("  " + line));
            System.out.println();
            return true;
        } catch (SQLException e) {
            System.out.println("Postgres refused the statement:");
            System.out.println();
            e.getMessage().lines().forEach(line -> System.out.println("    " + line));
            System.out.println();
            return false;
        } finally {
            connection.rollback();
            connection.setAutoCommit(true);
            connection.setReadOnly(false);
        }
    }

    private static void printHelp() {
        System.out.println("Usage: ./inspect                  (Windows: .\\inspect)");
        System.out.println("       ./inspect \"SELECT ...\"      (Windows: .\\inspect \"SELECT ...\")");
        System.out.println();
        System.out.println("With no argument: the four tables (rows counted now, rows the planner assumes, size,");
        System.out.println("when ANALYZE last ran) and what the planner believes about orders.status.");
        System.out.println();
        System.out.println("With a SELECT in double quotes: the first " + PREVIEW_ROWS + " rows it returns, the plan");
        System.out.println("Postgres used, and the facts pulled out of the plan: the join type that really ran,");
        System.out.println("rows read against rows returned, estimated against actual rows, whether a sort");
        System.out.println("spilled to disk, and where the time went. Then the four tables.");
        System.out.println();
        System.out.println("Only one read-only SELECT is accepted. It runs in a READ ONLY transaction.");
        System.out.println("It runs against whatever ./demo loaded last: the small shop or the big one.");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  ./inspect \"SELECT COUNT(*), COUNT(customer_id), COUNT(DISTINCT customer_id) FROM orders\"");
        System.out.println("  ./inspect \"SELECT c.name, o.id FROM customers c LEFT JOIN orders o ON o.customer_id = c.id\"");
    }

    private Inspect() {
    }
}
