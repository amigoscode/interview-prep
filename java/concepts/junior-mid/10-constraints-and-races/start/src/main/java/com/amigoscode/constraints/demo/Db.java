package com.amigoscode.constraints.demo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Connection settings and table printing shared by the demo and inspect tools. Same defaults as application.properties. */
final class Db {

    static final String URL = env("DB_URL", "jdbc:postgresql://localhost:5449/app");
    static final String USER = env("DB_USER", "app");
    static final String PASSWORD = env("DB_PASSWORD", "app");

    private Db() {
    }

    static Connection connect() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    /** Returns false, after printing what to do, when Postgres is not reachable. */
    static boolean isUp() {
        try (Connection ignored = connect()) {
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

    static int count(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }

    /** Prints a query result as a plain text table and returns the number of rows. NULL is printed as NULL. */
    static int printTable(Connection connection, String sql) throws SQLException {
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
                    row[c] = value == null ? "NULL" : value;
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

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
