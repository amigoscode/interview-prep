package com.amigoscode.sqltraps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.amigoscode.sqltraps.db.Plan;
import com.amigoscode.sqltraps.shop.ShopQueries;
import com.amigoscode.sqltraps.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Task 2: the report of PAID orders per customer must keep the customers who have none. */
@Tag("task2")
class PaidOrdersReportIT extends IntegrationTest {

    @BeforeEach
    void data() {
        smallShop();
    }

    @Test
    @DisplayName("Paid orders report: all 8 customers are in it, also the 4 who have no PAID order")
    void everyCustomerIsInTheReport() {
        assertThat(ids(rows(ShopQueries.paidOrdersPerCustomer())))
                .as("customer ids in the report")
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
    }

    @Test
    @DisplayName("Paid orders report: a customer with no PAID order shows 0 (not 1), the others their real count")
    void customersWithoutAPaidOrderShowZero() {
        assertThat(byId(rows(ShopQueries.paidOrdersPerCustomer()), "paid_orders"))
                .as("paid_orders by customer id")
                .containsExactly(entry(1L, 2L), entry(2L, 1L), entry(3L, 0L), entry(4L, 0L),
                        entry(5L, 0L), entry(6L, 3L), entry(7L, 0L), entry(8L, 1L));
    }

    @Test
    @DisplayName("Paid orders report: Postgres runs it as an outer join (Left or Right), not as an Inner join")
    void postgresRunsAnOuterJoin() {
        Plan plan = plan(ShopQueries.paidOrdersPerCustomer());

        assertThat(plan.joinTypes()).as("join types in the plan: %s", plan).isNotEmpty().doesNotContain("Inner");
        assertThat(plan.joinTypes()).as("join types in the plan: %s", plan).containsAnyOf("Left", "Right");
    }
}
