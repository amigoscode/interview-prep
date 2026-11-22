package com.amigoscode.transactional.demo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Connection settings shared by the demo and inspect tools. Same defaults as application.properties. */
final class Db {

    static final String URL = env("DB_URL", "jdbc:postgresql://localhost:5442/bank");
    static final String USER = env("DB_USER", "bank");
    static final String PASSWORD = env("DB_PASSWORD", "bank");

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

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
