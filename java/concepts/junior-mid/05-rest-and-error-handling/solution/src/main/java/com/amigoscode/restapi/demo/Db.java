package com.amigoscode.restapi.demo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** Database helpers shared by the demo and inspect tools. Same defaults as application.properties. */
final class Db {

    static final String URL = env("DB_URL", "jdbc:postgresql://localhost:5446/orders");
    static final String USER = env("DB_USER", "orders");
    static final String PASSWORD = env("DB_PASSWORD", "orders");

    private Db() {
    }

    /** Returns false, after printing what to do, when Postgres is not reachable. */
    static boolean isUp() {
        try (Connection ignored = DriverManager.getConnection(URL, USER, PASSWORD)) {
            return true;
        } catch (SQLException e) {
            System.out.println();
            System.out.println("Cannot reach Postgres at " + URL);
            System.out.println("Start it first, from this folder:");
            System.out.println();
            System.out.println("    docker compose up -d");
            System.out.println();
            System.out.println("(" + e.getMessage().lines().findFirst().orElse("") + ")");
            return false;
        }
    }

    /** Back to the six orders of seed.sql. Every run of ./demo and ./inspect starts here. */
    static void reset(DataSource dataSource) {
        new ResourceDatabasePopulator(new ClassPathResource("seed.sql")).execute(dataSource);
    }

    /** Every order row, by id, as column -> value. Two snapshots can be compared with printChanges. */
    static Map<Long, Map<String, String>> snapshot(JdbcTemplate jdbc) {
        Map<Long, Map<String, String>> rows = new LinkedHashMap<>();
        jdbc.query("select * from orders order by id", result -> {
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 1; c <= result.getMetaData().getColumnCount(); c++) {
                String value = result.getString(c);
                row.put(result.getMetaData().getColumnLabel(c), value == null ? "(empty)" : value);
            }
            rows.put(result.getLong("id"), row);
        });
        return rows;
    }

    /** One line per column that differs between two snapshots. */
    static List<String> changes(Map<Long, Map<String, String>> before, Map<Long, Map<String, String>> after) {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<Long, Map<String, String>> entry : before.entrySet()) {
            Map<String, String> now = after.get(entry.getKey());
            if (now == null) {
                lines.add("order " + entry.getKey() + "  row DELETED");
                continue;
            }
            for (Map.Entry<String, String> column : entry.getValue().entrySet()) {
                String newValue = now.get(column.getKey());
                if (!column.getValue().equals(newValue)) {
                    lines.add("order " + entry.getKey() + "  " + column.getKey() + ": "
                            + column.getValue() + " -> " + newValue);
                }
            }
        }
        for (Map.Entry<Long, Map<String, String>> entry : after.entrySet()) {
            if (!before.containsKey(entry.getKey())) {
                Map<String, String> row = entry.getValue();
                lines.add("order " + entry.getKey() + "  NEW ROW: customer=" + quote(row.get("customer"))
                        + " product=" + row.get("product") + " quantity=" + row.get("quantity")
                        + " total=" + row.get("total") + " status=" + row.get("status"));
            }
        }
        return lines;
    }

    static void printChanges(Map<Long, Map<String, String>> before, Map<Long, Map<String, String>> after) {
        List<String> lines = changes(before, after);
        if (lines.isEmpty()) {
            System.out.println("  In the database: nothing changed");
        } else {
            System.out.println("  In the database:");
            lines.forEach(line -> System.out.println("     " + line));
        }
        System.out.println();
    }

    private static String quote(String value) {
        return "'" + value + "'";
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
