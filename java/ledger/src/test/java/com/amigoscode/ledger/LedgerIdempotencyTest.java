package com.amigoscode.ledger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** Story 4, the idempotency-key extension. */
class LedgerIdempotencyTest {

    Ledger ledger;

    @BeforeEach
    void setUp() {
        ledger = new Ledger();
        ledger.open("A", new BigDecimal("100.00"));
        ledger.open("B", new BigDecimal("0"));
    }

    @Test
    void replayingTheSameKeyDoesNotMoveMoneyTwice() {
        TransferReceipt first = ledger.transfer("req-1", "A", "B", new BigDecimal("30.00"));
        TransferReceipt replay = ledger.transfer("req-1", "A", "B", new BigDecimal("30.00"));

        assertThat(first.succeeded()).isTrue();
        assertThat(replay).isEqualTo(first);
        assertThat(ledger.balance("A")).isEqualByComparingTo("70.00");
        assertThat(ledger.balance("B")).isEqualByComparingTo("30.00");
    }

    @Test
    void aFailedTransferIsRememberedToo() {
        TransferReceipt first = ledger.transfer("req-2", "A", "B", new BigDecimal("500.00"));
        ledger.deposit("A", new BigDecimal("1000.00"));
        TransferReceipt replay = ledger.transfer("req-2", "A", "B", new BigDecimal("500.00"));

        assertThat(first.succeeded()).isFalse();
        assertThat(replay).isEqualTo(first);              // still the original outcome
        assertThat(ledger.balance("B")).isEqualByComparingTo("0");
    }

    @Test
    void racingReplaysStillMoveMoneyOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<TransferReceipt>> results = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return ledger.transfer("req-3", "A", "B", new BigDecimal("10.00"));
            }));
        }
        start.countDown();
        for (Future<TransferReceipt> r : results) assertThat(r.get().succeeded()).isTrue();
        pool.shutdown();

        assertThat(ledger.balance("A")).isEqualByComparingTo("90.00");
        assertThat(ledger.balance("B")).isEqualByComparingTo("10.00");
    }
}
