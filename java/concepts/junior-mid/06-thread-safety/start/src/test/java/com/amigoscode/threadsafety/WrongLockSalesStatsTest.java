package com.amigoscode.threadsafety;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.threadsafety.sales.SalesSummary;
import com.amigoscode.threadsafety.sales.WrongLockSalesStats;
import com.amigoscode.threadsafety.support.Race;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Task 9. This test passes on purpose: it pins down that a lock on the wrong object protects nothing. */
@Tag("task9")
class WrongLockSalesStatsTest {

    private static final int WRITERS = 8;
    private static final int SALES_EACH = 50_000;
    private static final long SALE_CENTS = 500;
    private static final int MAX_ROUNDS = 20;

    @Test
    @DisplayName("Wrong lock: record() is synchronized on a new object per call, and sales are still lost (this documents the bug)")
    void aLockNobodySharesProtectsNothing() {
        SalesSummary expected = new SalesSummary(WRITERS * SALES_EACH, WRITERS * SALES_EACH * SALE_CENTS);
        boolean wrong = false;
        for (int round = 1; round <= MAX_ROUNDS && !wrong; round++) {
            WrongLockSalesStats stats = new WrongLockSalesStats();
            Race.run(WRITERS, SALES_EACH, (thread, i) -> stats.record(SALE_CENTS));
            wrong = !stats.summary().equals(expected);
        }

        assertThat(wrong).as("the totals were wrong in at least one round").isTrue();
    }
}
