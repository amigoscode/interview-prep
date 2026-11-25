package com.amigoscode.threadsafety;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.threadsafety.sales.SalesStats;
import com.amigoscode.threadsafety.sales.SalesSummary;
import com.amigoscode.threadsafety.support.Race;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Task 8. Unit tests: plain Java, no Spring. Every sale is 5.00, so a summary is only right when
 * revenue = orders x 5.00. One thread reads summaries while eight threads record sales.
 */
@Tag("task8")
class SalesStatsTest {

    private static final int WRITERS = 8;
    private static final int SALES_EACH = 50_000;
    private static final long SALE_CENTS = 500;
    private static final int ROUNDS = 5;

    @Test
    @DisplayName("Sales: while 8 threads record sales of 5.00, every summary a reader takes adds up: revenue = orders x 5.00")
    void everySummaryAddsUp() {
        for (int round = 1; round <= ROUNDS; round++) {
            SalesStats stats = new SalesStats();
            AtomicInteger doNotAddUp = new AtomicInteger();
            AtomicReference<SalesSummary> example = new AtomicReference<>();

            // Thread 0 is the dashboard: it only reads. Threads 1 to 8 record sales.
            List<Throwable> thrown = Race.run(WRITERS + 1, SALES_EACH, (thread, i) -> {
                if (thread > 0) {
                    stats.record(SALE_CENTS);
                    return;
                }
                SalesSummary summary = stats.summary();
                if (summary.revenueCents() != summary.orders() * SALE_CENTS) {
                    doNotAddUp.incrementAndGet();
                    example.compareAndSet(null, summary);
                }
            });

            assertThat(Race.describe(thrown)).isEmpty();
            assertThat(doNotAddUp.get())
                    .withFailMessage("%,d of %,d summaries do not add up. For example %s: %,d orders x 500 is %,d.",
                            doNotAddUp.get(), SALES_EACH, example.get(),
                            example.get() == null ? 0 : example.get().orders(),
                            example.get() == null ? 0 : example.get().orders() * SALE_CENTS)
                    .isZero();
        }
    }

    @Test
    @DisplayName("Sales: 8 threads x 50,000 sales of 5.00. The final totals are exact: 400,000 orders, 2,000,000.00")
    void finalTotalsAreExact() {
        SalesStats stats = new SalesStats();

        assertThat(Race.describe(Race.run(WRITERS, SALES_EACH, (thread, i) -> stats.record(SALE_CENTS)))).isEmpty();

        assertThat(stats.summary()).isEqualTo(new SalesSummary(WRITERS * SALES_EACH, WRITERS * SALES_EACH * SALE_CENTS));
    }
}
