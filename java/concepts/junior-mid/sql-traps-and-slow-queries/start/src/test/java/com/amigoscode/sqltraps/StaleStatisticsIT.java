package com.amigoscode.sqltraps;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.sqltraps.db.Plan;
import com.amigoscode.sqltraps.shop.ShopQueries;
import com.amigoscode.sqltraps.support.IntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Task 9. These tests pass on purpose: they document what stale statistics do to a plan.
 * 300,000 orders are loaded on top of statistics that describe 12 orders, the "expired orders"
 * query is planned, then ANALYZE runs and the same query is planned again.
 */
@Tag("task9")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StaleStatisticsIT extends IntegrationTest {

    private static final long EXPIRED_ORDERS = 90_000;

    private Plan stale;
    private Plan fresh;

    @BeforeAll
    void planBeforeAndAfterAnalyze() {
        bigShopWithStaleStatistics();
        stale = plan(ShopQueries.expiredOrders());
        analyze();
        fresh = plan(ShopQueries.expiredOrders());
    }

    @Test
    @DisplayName("Stale statistics: the planner expects 1 EXPIRED order, gets 90,000, and has chosen a Nested Loop (this documents the problem)")
    void staleStatisticsGiveAWrongEstimate() {
        Plan.Node orders = stale.scanOf("orders").orElseThrow();

        assertThat(orders.actualRows()).as("EXPIRED orders really in the table").isEqualTo(EXPIRED_ORDERS);
        assertThat(orders.estimatedRows()).as("EXPIRED orders the planner expected").isEqualTo(1);
        assertThat(stale.has("Nested Loop")).as("plan with stale statistics: %s", stale).isTrue();
        assertThat(stale.rowsReturned()).isEqualTo(EXPIRED_ORDERS);
    }

    @Test
    @DisplayName("Stale statistics: after ANALYZE the planner expects 90,000, switches to a Hash Join and touches 10 times fewer pages")
    void afterAnalyzeThePlanChanges() {
        Plan.Node orders = fresh.scanOf("orders").orElseThrow();

        assertThat(orders.estimatedRows()).as("EXPIRED orders the planner expected after ANALYZE")
                .isBetween(EXPIRED_ORDERS / 2, EXPIRED_ORDERS * 2);
        assertThat(fresh.has("Hash Join")).as("plan after ANALYZE: %s", fresh).isTrue();
        assertThat(fresh.has("Nested Loop")).as("plan after ANALYZE: %s", fresh).isFalse();
        assertThat(fresh.rowsReturned()).as("same rows, different plan").isEqualTo(stale.rowsReturned());
        assertThat(fresh.pages()).as("pages touched after ANALYZE, against %d before", stale.pages())
                .isLessThan(stale.pages() / 10);
    }
}
