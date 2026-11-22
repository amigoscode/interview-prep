package com.amigoscode.sqltraps;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.sqltraps.db.Plan;
import com.amigoscode.sqltraps.shop.Query;
import com.amigoscode.sqltraps.shop.ShopQueries;
import com.amigoscode.sqltraps.support.IntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Task 9. These tests pass on purpose: they document why "it was fast on my machine" proves nothing.
 * The same two queries are planned on the small shop (12 orders) and on the big one (300,012).
 */
@Tag("task9")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SmallVsBigIT extends IntegrationTest {

    private static final Query BY_CUSTOMER = ShopQueries.ordersOfCustomer(6);
    private static final Query BY_DAY = ShopQueries.ordersOfDay(LocalDate.of(2023, 2, 1));

    private Plan smallByCustomer;
    private Plan smallByDay;
    private Plan bigByCustomer;
    private Plan bigByDay;

    @BeforeAll
    void planOnBothShops() {
        smallShop();
        smallByCustomer = plan(BY_CUSTOMER);
        smallByDay = plan(BY_DAY);
        bigShop();
        bigByCustomer = plan(BY_CUSTOMER);
        bigByDay = plan(BY_DAY);
    }

    @Test
    @DisplayName("Dev vs prod: without an index the plan is the same Seq Scan, but it reads 12 rows in dev and 300,012 in prod (this documents the problem)")
    void theSamePlanDoesMoreWorkOnMoreRows() {
        assertThat(smallByCustomer.has("Seq Scan")).isTrue();
        assertThat(bigByCustomer.has("Seq Scan")).isTrue();
        assertThat(smallByCustomer.rowsReturned()).isEqualTo(3);
        assertThat(bigByCustomer.rowsReturned()).isEqualTo(3);
        assertThat(smallByCustomer.rowsRead()).as("rows read in dev").isEqualTo(12);
        assertThat(bigByCustomer.rowsRead()).as("rows read in prod").isEqualTo(300_012);
    }

    @Test
    @DisplayName("Dev vs prod: the index on created_at is ignored on a table of one page and used on the big table (this documents the problem)")
    void theIndexIsOnlyUsedOnTheBigTable() {
        assertThat(smallByDay.has("Seq Scan")).as("plan in dev: %s", smallByDay).isTrue();
        assertThat(smallByDay.indexes()).as("indexes used in dev").isEmpty();
        assertThat(bigByDay.has("Seq Scan")).as("plan in prod: %s", bigByDay).isFalse();
        assertThat(bigByDay.indexes()).as("indexes used in prod").containsExactly("idx_orders_created_at");
        assertThat(smallByDay.rowsReturned()).isEqualTo(bigByDay.rowsReturned()).isEqualTo(1);
    }
}
