package com.amigoscode.sqltraps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.amigoscode.sqltraps.shop.ShopQueries;
import com.amigoscode.sqltraps.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Task 5: joining orders to their lines must not count an order once per line. */
@Tag("task5")
class RevenueReportIT extends IntegrationTest {

    @BeforeEach
    void data() {
        smallShop();
    }

    @Test
    @DisplayName("Revenue report: every PAID order is counted once: Ada 2, Grace 1, Barbara 3, Edsger 1")
    void everyOrderIsCountedOnce() {
        assertThat(byId(rows(ShopQueries.revenuePerCustomer()), "orders"))
                .as("orders by customer id")
                .containsExactly(entry(1L, 2L), entry(2L, 1L), entry(6L, 3L), entry(8L, 1L));
    }

    @Test
    @DisplayName("Revenue report: revenue is the sum of the order totals: 180.00, 90.00, 120.00, 90.00, together 480.00")
    void revenueIsNotInflated() {
        Map<Long, Long> revenue = byId(rows(ShopQueries.revenuePerCustomer()), "revenue_cents");

        assertThat(revenue)
                .as("revenue_cents by customer id")
                .containsExactly(entry(1L, 18_000L), entry(2L, 9_000L), entry(6L, 12_000L), entry(8L, 9_000L));
        assertThat(revenue.values().stream().mapToLong(Long::longValue).sum())
                .as("revenue of the whole report against the PAID orders that have a customer")
                .isEqualTo(jdbc.queryForObject(
                        "select sum(total_cents) from orders where status = 'PAID' and customer_id is not null", Long.class));
    }

    @Test
    @DisplayName("Revenue report: units bought are still right: 3, 4, 6, 2 (already passing: keep it that way)")
    void unitsAreStillRight() {
        assertThat(byId(rows(ShopQueries.revenuePerCustomer()), "units"))
                .as("units by customer id")
                .containsExactly(entry(1L, 3L), entry(2L, 4L), entry(6L, 6L), entry(8L, 2L));
    }
}
