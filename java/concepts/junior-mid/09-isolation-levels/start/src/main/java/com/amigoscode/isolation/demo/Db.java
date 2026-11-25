package com.amigoscode.isolation.demo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** Connection settings shared by the demo and inspect tools. Same defaults as application.properties. */
public final class Db {

    static final String URL = env("DB_URL", "jdbc:postgresql://localhost:5448/bank");
    static final String USER = env("DB_USER", "bank");
    static final String PASSWORD = env("DB_PASSWORD", "bank");

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

    /** Creates the table if needed and puts the data back: alice, current 500 and savings 500. */
    public static void reset(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(schema());
            statement.execute("delete from account");
            statement.execute("insert into account (id, owner, name, balance) "
                    + "values (1, 'alice', 'current', 500), (2, 'alice', 'savings', 500)");
        }
    }

    private static String schema() {
        try (InputStream in = Db.class.getResourceAsStream("/schema.sql")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read schema.sql", e);
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
