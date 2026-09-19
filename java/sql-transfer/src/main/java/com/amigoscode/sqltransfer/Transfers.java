package com.amigoscode.sqltransfer;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Three ways to move money between two rows, each one a single transaction.
 *
 * <p>The naive version is what everyone writes first and it is correct on its own. Under
 * concurrency it loses updates, because "read the balance" and "write the new balance" are two
 * statements with a gap between them, and READ COMMITTED lets another transaction commit into
 * that gap. The two fixes close the gap differently: pessimistic locking makes the read take a
 * row lock so nobody else can enter; optimistic locking lets everyone in and makes the write
 * prove nothing changed since the read.
 */
public final class Transfers {

    private static final int MAX_OPTIMISTIC_RETRIES = 20;
    private final Database db;

    public Transfers(Database db) {
        this.db = db;
    }

    /** Story 1: read, check, debit, credit, commit. Correct alone, wrong under concurrency. */
    public void transferNaive(String fromId, String toId, BigDecimal amount) {
        requirePositive(amount);
        try (Connection c = db.connect()) {
            try {
                naiveSteps(c, fromId, toId, amount);
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The steps as separate methods so the interleaving tests can run them one by one. */
    public static void naiveSteps(Connection c, String fromId, String toId, BigDecimal amount) throws SQLException {
        BigDecimal balance = readBalance(c, fromId);
        if (balance.compareTo(amount) < 0) throw new InsufficientFundsException(fromId);
        writeBalance(c, fromId, balance.subtract(amount));
        writeBalance(c, toId, readBalance(c, toId).add(amount));
    }

    /**
     * Story 3: lock both rows first with SELECT ... FOR UPDATE, in id order. The second
     * transaction blocks on the lock until the first commits, then reads the committed value.
     * Id order matters for the same reason as in Java: two transfers in opposite directions
     * would otherwise each hold one row and wait for the other, and the database would have to
     * kill one of them.
     */
    public void transferPessimistic(String fromId, String toId, BigDecimal amount) {
        requirePositive(amount);
        try (Connection c = db.connect()) {
            try {
                pessimisticSteps(c, fromId, toId, amount);
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void pessimisticSteps(Connection c, String fromId, String toId, BigDecimal amount) throws SQLException {
        String first = fromId.compareTo(toId) < 0 ? fromId : toId;
        String second = first.equals(fromId) ? toId : fromId;
        BigDecimal firstBalance = lockAndRead(c, first);
        BigDecimal secondBalance = lockAndRead(c, second);
        BigDecimal fromBalance = first.equals(fromId) ? firstBalance : secondBalance;
        BigDecimal toBalance = first.equals(fromId) ? secondBalance : firstBalance;
        if (fromBalance.compareTo(amount) < 0) throw new InsufficientFundsException(fromId);
        writeBalance(c, fromId, fromBalance.subtract(amount));
        writeBalance(c, toId, toBalance.add(amount));
    }

    /**
     * Story 4: no locks held while thinking. Read balance and version, then update only if the
     * version is still the one we read. Zero rows updated means someone else committed first:
     * roll back, back off briefly, and try again from the top, a bounded number of times.
     */
    public void transferOptimistic(String fromId, String toId, BigDecimal amount) {
        requirePositive(amount);
        for (int attempt = 1; ; attempt++) {
            try (Connection c = db.connect()) {
                try {
                    if (optimisticSteps(c, fromId, toId, amount)) {
                        c.commit();
                        return;
                    }
                    c.rollback();
                    if (attempt >= MAX_OPTIMISTIC_RETRIES) {
                        throw new IllegalStateException("gave up after " + attempt + " optimistic attempts");
                    }
                    backOff(attempt);
                } catch (Exception e) {
                    c.rollback();
                    throw e;
                }
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** One attempt. Returns false when a version check failed and the caller should retry. */
    public static boolean optimisticSteps(Connection c, String fromId, String toId, BigDecimal amount) throws SQLException {
        Versioned from = readVersioned(c, fromId);
        Versioned to = readVersioned(c, toId);
        if (from.balance.compareTo(amount) < 0) throw new InsufficientFundsException(fromId);
        // An UPDATE takes a row lock even without FOR UPDATE, so two transfers in opposite
        // directions deadlock unless every transaction writes the rows in the same order.
        if (fromId.compareTo(toId) < 0) {
            return writeIfVersion(c, fromId, from.balance.subtract(amount), from.version)
                && writeIfVersion(c, toId, to.balance.add(amount), to.version);
        }
        return writeIfVersion(c, toId, to.balance.add(amount), to.version)
            && writeIfVersion(c, fromId, from.balance.subtract(amount), from.version);
    }

    /**
     * Story 5, bonus: one statement per side, with the balance check inside the UPDATE's WHERE.
     * Safe without an explicit lock because a single UPDATE is atomic: the database locks the
     * row, re-evaluates the WHERE against the current committed value, and applies the change.
     */
    public void transferOneStatement(String fromId, String toId, BigDecimal amount) {
        requirePositive(amount);
        try (Connection c = db.connect()) {
            try {
                // Same rule as everywhere else: rows are written in id order, because each
                // UPDATE holds its row lock until commit and opposite orders deadlock.
                if (fromId.compareTo(toId) < 0) {
                    debitIfEnough(c, fromId, amount);
                    credit(c, toId, amount);
                } else {
                    credit(c, toId, amount);
                    debitIfEnough(c, fromId, amount);
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void debitIfEnough(Connection c, String id, BigDecimal amount) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "update accounts set balance = balance - ? where id = ? and balance >= ?")) {
            ps.setBigDecimal(1, amount);
            ps.setString(2, id);
            ps.setBigDecimal(3, amount);
            if (ps.executeUpdate() == 0) throw new InsufficientFundsException(id);
        }
    }

    private static void credit(Connection c, String id, BigDecimal amount) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("update accounts set balance = balance + ? where id = ?")) {
            ps.setBigDecimal(1, amount);
            ps.setString(2, id);
            if (ps.executeUpdate() == 0) throw new IllegalArgumentException("no such account: " + id);
        }
    }

    // ---- SQL helpers, public so the interleaving tests can script the steps ----

    public static BigDecimal readBalance(Connection c, String id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("select balance from accounts where id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("no such account: " + id);
                return rs.getBigDecimal(1);
            }
        }
    }

    public static BigDecimal lockAndRead(Connection c, String id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("select balance from accounts where id = ? for update")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("no such account: " + id);
                return rs.getBigDecimal(1);
            }
        }
    }

    public static void writeBalance(Connection c, String id, BigDecimal balance) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("update accounts set balance = ? where id = ?")) {
            ps.setBigDecimal(1, balance);
            ps.setString(2, id);
            ps.executeUpdate();
        }
    }

    public record Versioned(BigDecimal balance, int version) { }

    public static Versioned readVersioned(Connection c, String id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("select balance, version from accounts where id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("no such account: " + id);
                return new Versioned(rs.getBigDecimal(1), rs.getInt(2));
            }
        }
    }

    public static boolean writeIfVersion(Connection c, String id, BigDecimal balance, int expectedVersion) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "update accounts set balance = ?, version = version + 1 where id = ? and version = ?")) {
            ps.setBigDecimal(1, balance);
            ps.setString(2, id);
            ps.setInt(3, expectedVersion);
            return ps.executeUpdate() == 1;
        }
    }

    /**
     * Without this, four threads hammering the same two rows starve each other: every retry
     * collides again and the loop gives up. Jittered backoff is the standard fix, and the fact
     * that it is needed at all is the real answer to "optimistic or pessimistic?": optimistic
     * locking is for rows that are rarely contended. A hot account belongs under FOR UPDATE.
     */
    private static void backOff(int attempt) {
        try {
            Thread.sleep(java.util.concurrent.ThreadLocalRandom.current().nextLong(1, 5L * attempt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while retrying", e);
        }
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("amount must be positive");
    }
}
