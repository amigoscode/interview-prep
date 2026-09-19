package com.amigoscode.sqltransfer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/** Real threads, not scripted: the safe variants conserve money; the naive one is not run here on purpose. */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class ConcurrentTransfersTest {

    Database db;
    Transfers transfers;

    @BeforeEach
    void setUp() {
        db = new Database();
        db.createAccount("A", new BigDecimal("1000.00"));
        db.createAccount("B", new BigDecimal("1000.00"));
        transfers = new Transfers(db);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void pessimisticTransfersInBothDirectionsConserveMoney() throws Exception {
        hammer(t -> transfers.transferPessimistic(t.from, t.to, BigDecimal.ONE));
    }

    @Test
    void optimisticTransfersInBothDirectionsConserveMoney() throws Exception {
        hammer(t -> transfers.transferOptimistic(t.from, t.to, BigDecimal.ONE));
    }

    @Test
    void oneStatementTransfersInBothDirectionsConserveMoney() throws Exception {
        hammer(t -> transfers.transferOneStatement(t.from, t.to, BigDecimal.ONE));
    }

    record Transfer(String from, String to) { }

    private void hammer(Consumer<Transfer> transfer) throws Exception {
        int threads = 4, perThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> tasks = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            Transfer direction = t % 2 == 0 ? new Transfer("A", "B") : new Transfer("B", "A");
            tasks.add(pool.submit(() -> {
                for (int i = 0; i < perThread; i++) transfer.accept(direction);
                return null;
            }));
        }
        for (Future<?> task : tasks) task.get();
        pool.shutdown();

        assertThat(db.balance("A").add(db.balance("B"))).isEqualByComparingTo("2000.00");
    }
}
