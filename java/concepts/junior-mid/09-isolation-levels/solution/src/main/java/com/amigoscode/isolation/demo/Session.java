package com.amigoscode.isolation.demo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * One database session (one connection) that is driven one statement at a time. Each statement is
 * sent as plain SQL, including "begin" and "commit", so the timeline shows exactly what Postgres
 * received.
 */
final class Session implements AutoCloseable {

    /** What one statement returned: rows, a row count, or an error. */
    record Result(List<List<String>> rows, int rowCount, String sqlState, String error) {

        boolean failed() {
            return sqlState != null;
        }

        /** The first column of the first row. */
        String value() {
            return rows.get(0).get(0);
        }

        long number() {
            return Long.parseLong(value());
        }

        /** The first column of every row, added up. */
        long sum() {
            return rows.stream().mapToLong(row -> Long.parseLong(row.get(0))).sum();
        }

        /** The first column of every row: "500 + 500". */
        String addition() {
            return rows.stream().map(row -> row.get(0)).collect(Collectors.joining(" + "));
        }
    }

    /** A statement that has been sent. It may still be waiting for a lock. */
    final class Pending {

        private final String sql;
        private final Future<Result> future;
        private final boolean waited;
        private Result result;

        private Pending(String sql, Future<Result> future, boolean waited) {
            this.sql = sql;
            this.future = future;
            this.waited = waited;
        }

        /** True when the statement did not return at once because another session holds a lock it needs. */
        boolean waited() {
            return waited;
        }

        /** Waits for the statement to finish, prints what came back, and returns it. */
        Result result() {
            if (result == null) {
                try {
                    result = future.get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new IllegalStateException(e);
                }
                report(sql, result, waited);
            }
            return result;
        }
    }

    private final String name;
    private final Connection connection;
    private final Connection monitor;
    private final Timeline timeline;
    private final int pid;
    private final ExecutorService thread;
    private boolean failed;

    Session(String name, Connection connection, Connection monitor, Timeline timeline) throws SQLException {
        this.name = name;
        this.connection = connection;
        this.monitor = monitor;
        this.timeline = timeline;
        this.thread = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "session-" + name);
            thread.setDaemon(true);
            return thread;
        });
        try (Statement statement = connection.createStatement()) {
            // The name ./inspect shows for this session.
            statement.execute("set application_name = 'demo " + name + "'");
            try (ResultSet row = statement.executeQuery("select pg_backend_pid()")) {
                row.next();
                this.pid = row.getInt(1);
            }
        }
    }

    /** Sends one statement and waits for it to finish. */
    Result exec(String sql) {
        return send(sql).result();
    }

    /**
     * Sends one statement and returns as soon as it has finished or Postgres reports that it is
     * waiting for a lock. Nothing here guesses with a timer: it asks pg_blocking_pids().
     */
    Pending send(String sql) {
        timeline.step(name, sql);
        Future<Result> future = thread.submit(() -> run(sql));
        boolean waiting = false;
        try {
            while (!future.isDone() && !(waiting = isWaitingForALock())) {
                Thread.sleep(20);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        Pending pending = new Pending(sql, future, waiting);
        if (waiting) {
            timeline.result(name, "WAITING. Another session holds a lock this statement needs");
        } else {
            pending.result();
        }
        return pending;
    }

    /** Ends the transaction: commit, or rollback when a statement in it has already failed. */
    Result end() {
        if (failed) {
            Result result = exec("rollback");
            timeline.comment(name, "this transaction was cancelled, so there is nothing to commit");
            return result;
        }
        return exec("commit");
    }

    /** Prints what the application does with a value it read, outside the database. */
    void comment(String text) {
        timeline.comment(name, text);
    }

    /** True when the database has cancelled this session's transaction. */
    boolean failed() {
        return failed;
    }

    private Result run(String sql) {
        try (Statement statement = connection.createStatement()) {
            List<List<String>> rows = new ArrayList<>();
            if (statement.execute(sql)) {
                try (ResultSet result = statement.getResultSet()) {
                    int columns = result.getMetaData().getColumnCount();
                    while (result.next()) {
                        List<String> row = new ArrayList<>();
                        for (int c = 1; c <= columns; c++) {
                            row.add(String.valueOf(result.getObject(c)));
                        }
                        rows.add(row);
                    }
                }
                return new Result(rows, rows.size(), null, null);
            }
            Result result = new Result(rows, statement.getUpdateCount(), null, null);
            if (sql.strip().toLowerCase(Locale.ROOT).startsWith("begin")) {
                reportIsolationLevel(statement);
            }
            return result;
        } catch (SQLException e) {
            return new Result(List.of(), 0, e.getSQLState(), e.getMessage());
        }
    }

    /**
     * Postgres has no column that shows another session's isolation level, so every demo session
     * puts its own level into its application_name, where ./inspect can read it. SHOW and SET do
     * not take a snapshot, so this changes nothing about what the transaction will see.
     */
    private void reportIsolationLevel(Statement statement) throws SQLException {
        String level;
        try (ResultSet row = statement.executeQuery("show transaction_isolation")) {
            row.next();
            level = row.getString(1);
        }
        statement.execute("set local application_name = 'demo " + name + " (" + level + ")'");
    }

    private void report(String sql, Result result, boolean waited) {
        String prefix = waited ? "(stopped waiting) " : "";
        String verb = sql.strip().toLowerCase(Locale.ROOT).split("\\s+")[0];
        if (result.failed()) {
            // The transaction is now unusable until it is rolled back. A failed COMMIT has already rolled it back.
            failed = !verb.equals("commit") && !verb.equals("rollback");
            List<String> lines = result.error().lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
            timeline.result(name, prefix + "ERROR, SQLSTATE " + result.sqlState() + ": "
                    + lines.get(0).replaceFirst("^ERROR: ", ""));
            lines.stream().skip(1).limit(2).forEach(line -> timeline.result(name, line));
        } else if (!result.rows().isEmpty()) {
            timeline.result(name, prefix + result.rows().stream()
                    .map(row -> String.join(" ", row)).collect(Collectors.joining(", ")));
        } else if (verb.equals("update") || verb.equals("insert") || verb.equals("delete")) {
            timeline.result(name, prefix + result.rowCount() + (result.rowCount() == 1 ? " row" : " rows"));
        } else if (verb.equals("rollback") || verb.equals("commit")) {
            failed = false;
        }
    }

    private boolean isWaitingForALock() {
        try (PreparedStatement statement = monitor.prepareStatement("select cardinality(pg_blocking_pids(?)) > 0")) {
            statement.setInt(1, pid);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return row.getBoolean(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        thread.shutdownNow();
        try {
            connection.close();
        } catch (SQLException ignored) {
            // closing anyway
        }
    }
}
