package com.amigoscode.sqltransfer;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * The environment, given. An in-memory H2 database in PostgreSQL mode with one table:
 *
 * <pre>
 *   accounts(id varchar primary key, balance decimal(18,2) check (balance >= 0), version int)
 * </pre>
 *
 * Each {@code Database} is a fresh, isolated schema; every {@link #connect()} is a new
 * session with autocommit off, so two connections behave like two concurrent clients.
 */
public final class Database {

    private final String url;

    public Database() {
        // DB_CLOSE_DELAY=-1 keeps the schema alive while any connection may still open it.
        // LOCK_TIMEOUT is how long a session waits for a row lock before H2 gives up.
        this.url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000";
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            s.execute("""
                create table accounts (
                  id      varchar(32)   primary key,
                  balance decimal(18,2) not null check (balance >= 0),
                  version int           not null default 0
                )""");
        } catch (SQLException e) {
            throw new IllegalStateException("could not create schema", e);
        }
    }

    /** A new session. Autocommit is OFF: you own BEGIN/COMMIT/ROLLBACK. */
    public Connection connect() {
        try {
            Connection c = DriverManager.getConnection(url);
            c.setAutoCommit(false);
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            return c;
        } catch (SQLException e) {
            throw new IllegalStateException("could not connect", e);
        }
    }

    public void createAccount(String id, BigDecimal balance) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("insert into accounts (id, balance) values (?, ?)")) {
            ps.setString(1, id);
            ps.setBigDecimal(2, balance);
            ps.executeUpdate();
            c.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("could not create account " + id, e);
        }
    }

    /** Committed balance as another session would see it. */
    public BigDecimal balance(String id) {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("select balance from accounts where id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("no such account: " + id);
                return rs.getBigDecimal(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("could not read balance of " + id, e);
        }
    }

    public void close() {
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            s.execute("shutdown");
        } catch (SQLException ignored) {
            // already gone
        }
    }
}
