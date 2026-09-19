package com.amigoscode.sqltransfer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Two sessions, scripted step by step. Each test is named after what it shows. These are the
 * answers to "which isolation level fixes your transfer" and "pessimistic or optimistic",
 * seen rather than recited.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class AnomaliesTest {

    Database db;
    Transfers transfers;

    @BeforeEach
    void setUp() {
        db = new Database();
        db.createAccount("A", new BigDecimal("100.00"));
        db.createAccount("B", new BigDecimal("0.00"));
        transfers = new Transfers(db);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    /**
     * Story 2. Both sessions read 100. Session 1 writes 80 and commits. Session 2, still
     * believing the balance is 100, writes 70 and commits. The 20 that session 1 moved has
     * vanished from A, yet B received both amounts: money was created. READ COMMITTED allows
     * this because each statement sees the latest commit, but nothing ties session 2's read
     * to its write.
     */
    @Test
    void naiveTransferLosesAnUpdateUnderReadCommitted() throws Exception {
        TwoSessions.run(db, (s1, s2) -> {
            AtomicReference<BigDecimal> seen1 = new AtomicReference<>();
            AtomicReference<BigDecimal> seen2 = new AtomicReference<>();
            s1.step(c -> seen1.set(Transfers.readBalance(c, "A")));
            s2.step(c -> seen2.set(Transfers.readBalance(c, "A")));
            assertThat(seen1.get()).isEqualByComparingTo("100.00");
            assertThat(seen2.get()).isEqualByComparingTo("100.00");

            s1.step(c -> { Transfers.writeBalance(c, "A", seen1.get().subtract(new BigDecimal("20"))); Transfers.writeBalance(c, "B", Transfers.readBalance(c, "B").add(new BigDecimal("20"))); c.commit(); });
            s2.step(c -> { Transfers.writeBalance(c, "A", seen2.get().subtract(new BigDecimal("30"))); Transfers.writeBalance(c, "B", Transfers.readBalance(c, "B").add(new BigDecimal("30"))); c.commit(); });
        });

        assertThat(db.balance("A")).isEqualByComparingTo("70.00");   // should be 50
        assertThat(db.balance("B")).isEqualByComparingTo("50.00");   // B got both: 20 created from nothing
    }

    /**
     * Story 3. Session 1 takes the row lock with FOR UPDATE. Session 2's FOR UPDATE blocks
     * until session 1 commits, then reads 80, not 100, and the arithmetic is right.
     */
    @Test
    void forUpdateMakesTheSecondReaderWaitAndSeeTheCommittedValue() throws Exception {
        TwoSessions.run(db, (s1, s2) -> {
            s1.step(c -> assertThat(Transfers.lockAndRead(c, "A")).isEqualByComparingTo("100.00"));

            AtomicReference<BigDecimal> seen2 = new AtomicReference<>();
            Future<?> blocked = s2.stepAsync(c -> seen2.set(Transfers.lockAndRead(c, "A")));
            Thread.sleep(300);
            assertThat(blocked.isDone()).as("session 2 must be waiting on the row lock").isFalse();

            s1.step(c -> { Transfers.writeBalance(c, "A", new BigDecimal("80.00")); c.commit(); });
            blocked.get(10, TimeUnit.SECONDS);
            assertThat(seen2.get()).isEqualByComparingTo("80.00");

            s2.step(c -> { Transfers.writeBalance(c, "A", seen2.get().subtract(new BigDecimal("30"))); c.commit(); });
        });

        assertThat(db.balance("A")).isEqualByComparingTo("50.00");
    }

    /**
     * Story 3, the other half. Locks taken in opposite orders: session 1 holds A and wants B,
     * session 2 holds B and wants A. Neither can proceed. The database ends it by failing one
     * of them (H2 raises a deadlock or lock-timeout error; Postgres reports "deadlock detected"
     * within a second). Id-ordered locking prevents this from ever arising.
     */
    @Test
    void oppositeLockOrderDeadlocksAndTheDatabaseKillsOneSession() throws Exception {
        TwoSessions.run(db, (s1, s2) -> {
            s1.step(c -> Transfers.lockAndRead(c, "A"));
            s2.step(c -> Transfers.lockAndRead(c, "B"));

            Future<?> s1WantsB = s1.stepAsync(c -> Transfers.lockAndRead(c, "B"));
            Future<?> s2WantsA = s2.stepAsync(c -> Transfers.lockAndRead(c, "A"));

            int failures = 0;
            for (Future<?> f : new Future<?>[] {s1WantsB, s2WantsA}) {
                try {
                    f.get(20, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(SQLException.class);
                    failures++;
                }
            }
            assertThat(failures).as("the database must break the cycle by failing at least one side").isGreaterThanOrEqualTo(1);
        });
    }

    /**
     * Story 4. Both sessions read version 0. Session 1's conditional update matches and
     * commits (version becomes 1). Session 2's conditional update matches 0 rows: its read is
     * stale, so it rolls back and retries from the top, now seeing 80.
     */
    @Test
    void optimisticVersionCheckRejectsTheStaleWriterWhoThenRetries() throws Exception {
        TwoSessions.run(db, (s1, s2) -> {
            AtomicReference<Transfers.Versioned> v1 = new AtomicReference<>();
            AtomicReference<Transfers.Versioned> v2 = new AtomicReference<>();
            s1.step(c -> v1.set(Transfers.readVersioned(c, "A")));
            s2.step(c -> v2.set(Transfers.readVersioned(c, "A")));
            assertThat(v1.get().version()).isZero();
            assertThat(v2.get().version()).isZero();

            s1.step(c -> {
                assertThat(Transfers.writeIfVersion(c, "A", new BigDecimal("80.00"), v1.get().version())).isTrue();
                c.commit();
            });
            s2.step(c -> {
                assertThat(Transfers.writeIfVersion(c, "A", new BigDecimal("70.00"), v2.get().version()))
                    .as("stale version must not win").isFalse();
                c.rollback();
            });
        });
        assertThat(db.balance("A")).isEqualByComparingTo("80.00");

        // The full method retries on its own and lands on the right number.
        transfers.transferOptimistic("A", "B", new BigDecimal("30.00"));
        assertThat(db.balance("A")).isEqualByComparingTo("50.00");
        assertThat(db.balance("B")).isEqualByComparingTo("30.00");
    }

    /** Story 5. The check lives inside the UPDATE, so there is no gap for anyone to enter. */
    @Test
    void oneStatementTransferHasNoGapToExploit() throws Exception {
        TwoSessions.run(db, (s1, s2) -> {
            s1.step(c -> { Transfers.naiveSteps(c, "A", "B", new BigDecimal("60.00")); }); // uncommitted: A=40 pending
            Future<?> second = s2.stepAsync(c -> {
                try (var ps = c.prepareStatement("update accounts set balance = balance - ? where id = ? and balance >= ?")) {
                    ps.setBigDecimal(1, new BigDecimal("60.00")); ps.setString(2, "A"); ps.setBigDecimal(3, new BigDecimal("60.00"));
                    assertThat(ps.executeUpdate()).as("A only had 40 left once session 1 committed").isZero();
                }
                c.rollback();
            });
            Thread.sleep(300);
            assertThat(second.isDone()).as("session 2 waits for session 1's row lock").isFalse();
            s1.step(c -> c.commit());
            second.get(10, TimeUnit.SECONDS);
        });
        assertThat(db.balance("A")).isEqualByComparingTo("40.00");
    }

    @Test
    void assertJIsAvailableForTheExceptionAssertions() {
        assertThatThrownBy(() -> { throw new InsufficientFundsException("A"); }).hasMessageContaining("A");
    }
}
