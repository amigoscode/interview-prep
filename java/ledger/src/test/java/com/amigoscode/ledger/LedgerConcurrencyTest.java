package com.amigoscode.ledger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 3. The test the interviewer wants to see. Run it against a version of Ledger.transfer
 * that locks in caller order instead of id order and it hangs: that is the deadlock.
 */
class LedgerConcurrencyTest {

    private static final int THREADS = 16;
    private static final int TRANSFERS_PER_THREAD = 2_000;

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void oppositeDirectionTransfersConserveMoneyAndDoNotDeadlock() throws Exception {
        Ledger ledger = new Ledger();
        ledger.open("A", new BigDecimal("1000.00"));
        ledger.open("B", new BigDecimal("1000.00"));

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> tasks = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            boolean forward = t % 2 == 0;
            tasks.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < TRANSFERS_PER_THREAD; i++) {
                    try {
                        if (forward) ledger.transfer("A", "B", BigDecimal.ONE);
                        else         ledger.transfer("B", "A", BigDecimal.ONE);
                    } catch (InsufficientFundsException expectedWhenOneSideRunsDry) {
                        // fine: the rule held, no overdraft
                    }
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> task : tasks) task.get(); // surfaces any exception from a worker
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        BigDecimal total = ledger.balance("A").add(ledger.balance("B"));
        assertThat(total).isEqualByComparingTo("2000.00");
        assertThat(ledger.balance("A").signum()).isGreaterThanOrEqualTo(0);
        assertThat(ledger.balance("B").signum()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void concurrentWithdrawalsNeverOverdraw() throws Exception {
        Ledger ledger = new Ledger();
        ledger.open("A", new BigDecimal("100.00"));

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int t = 0; t < 200; t++) {           // 200 attempts to withdraw 1.00 from 100.00
            results.add(pool.submit(() -> {
                start.await();
                try {
                    ledger.withdraw("A", BigDecimal.ONE);
                    return true;
                } catch (InsufficientFundsException e) {
                    return false;
                }
            }));
        }
        start.countDown();
        long succeeded = 0;
        for (Future<Boolean> r : results) if (r.get()) succeeded++;
        pool.shutdown();

        assertThat(succeeded).isEqualTo(100);      // exactly the money that was there
        assertThat(ledger.balance("A")).isEqualByComparingTo("0");
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void transfersOnUnrelatedAccountsRunInParallel() throws Exception {
        // Not a strict benchmark, but a per-ledger lock would make this visibly slower than
        // per-account locks. Mostly here so you can talk about it.
        Ledger ledger = new Ledger();
        for (int i = 0; i < THREADS * 2; i++) ledger.open("acc" + i, new BigDecimal("5000"));

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Future<?>> tasks = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            String from = "acc" + (2 * t), to = "acc" + (2 * t + 1);
            tasks.add(pool.submit(() -> {
                for (int i = 0; i < TRANSFERS_PER_THREAD; i++) ledger.transfer(from, to, BigDecimal.ONE);
                return null;
            }));
        }
        for (Future<?> task : tasks) task.get();
        pool.shutdown();

        for (int t = 0; t < THREADS; t++) {
            assertThat(ledger.balance("acc" + (2 * t))).isEqualByComparingTo(String.valueOf(5000 - TRANSFERS_PER_THREAD));
            assertThat(ledger.balance("acc" + (2 * t + 1))).isEqualByComparingTo(String.valueOf(5000 + TRANSFERS_PER_THREAD));
        }
    }
}
