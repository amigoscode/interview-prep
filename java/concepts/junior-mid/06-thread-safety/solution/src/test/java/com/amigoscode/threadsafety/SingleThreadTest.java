package com.amigoscode.threadsafety;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.threadsafety.pages.PageViewStats;
import com.amigoscode.threadsafety.pricing.PriceCache;
import com.amigoscode.threadsafety.receipt.Receipt;
import com.amigoscode.threadsafety.receipt.ReceiptService;
import com.amigoscode.threadsafety.sales.SalesStats;
import com.amigoscode.threadsafety.sales.SalesSummary;
import com.amigoscode.threadsafety.shipping.ShippingLabels;
import com.amigoscode.threadsafety.support.ControllablePriceSource;
import com.amigoscode.threadsafety.visits.VisitCounter;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests: plain Java, no Spring, ONE thread. They pass from the start, in the broken project
 * too, and that is the lesson: a test that uses one thread cannot see any of these bugs. They must
 * keep passing while you fix things.
 */
@Tag("baseline")
class SingleThreadTest {

    @Test
    @DisplayName("One thread: 1,000 visits are counted as 1,000")
    void visitsAreCounted() {
        VisitCounter counter = new VisitCounter();

        for (int i = 0; i < 1_000; i++) {
            counter.hit();
        }

        assertThat(counter.count()).isEqualTo(1_000);
    }

    @Test
    @DisplayName("One thread: page views are counted per page")
    void pageViewsAreCountedPerPage() {
        PageViewStats stats = new PageViewStats();

        stats.record("home");
        stats.record("home");
        stats.record("ps5");

        assertThat(stats.views("home")).isEqualTo(2);
        assertThat(stats.views("ps5")).isEqualTo(1);
        assertThat(stats.views("never-viewed")).isEqualTo(0);
        assertThat(stats.pages()).isEqualTo(2);
    }

    @Test
    @DisplayName("One thread: a price is fetched from the supplier once, then served from the cache")
    void priceIsFetchedOnce() {
        ControllablePriceSource supplier = new ControllablePriceSource();
        PriceCache cache = new PriceCache(supplier);

        assertThat(cache.priceOf("ps5")).isEqualByComparingTo("499.00");
        assertThat(cache.priceOf("ps5")).isEqualByComparingTo("499.00");

        assertThat(supplier.calls("ps5")).isEqualTo(1);
    }

    @Test
    @DisplayName("One thread: a receipt shows the customer who paid, the amount and the points")
    void receiptShowsTheCustomerWhoPaid() {
        ReceiptService receipts = new ReceiptService((customer, amountCents) -> amountCents / 100);

        assertThat(receipts.receiptFor("alice", 2_500)).isEqualTo(new Receipt("alice", 2_500, 25));
        assertThat(receipts.receiptFor("bob", 900)).isEqualTo(new Receipt("bob", 900, 9));
    }

    @Test
    @DisplayName("One thread: a shipping label reads 'Ships on 05 Mar 2026'")
    void labelShowsTheShipDate() {
        ShippingLabels labels = new ShippingLabels();

        assertThat(labels.labelFor(LocalDate.of(2026, 3, 5))).isEqualTo("Ships on 05 Mar 2026");
        assertThat(labels.labelFor(LocalDate.of(2027, 12, 31))).isEqualTo("Ships on 31 Dec 2027");
    }

    @Test
    @DisplayName("One thread: three sales of 5.00 give 3 orders and 15.00 revenue")
    void salesAddUp() {
        SalesStats stats = new SalesStats();

        stats.record(500);
        stats.record(500);
        stats.record(500);

        assertThat(stats.summary()).isEqualTo(new SalesSummary(3, 1_500));
    }
}
