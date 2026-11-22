package com.amigoscode.sqltraps;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.sqltraps.db.Plan;
import com.amigoscode.sqltraps.shop.Query;
import com.amigoscode.sqltraps.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Task 1. This test passes on purpose: it pins down the trap so you can see exactly what goes wrong. */
@Tag("task1")
class LeftJoinTrapIT extends IntegrationTest {

    /** The report as it was first written. It stays here for ever, so you can always compare against it. */
    private static final Query TRAP = new Query("""
            SELECT c.id, c.name, COUNT(o.id) AS paid_orders
              FROM customers c
              LEFT JOIN orders o ON o.customer_id = c.id
             WHERE o.status = 'PAID'
             GROUP BY c.id, c.name
             ORDER BY c.id
            """);

    @BeforeEach
    void data() {
        smallShop();
    }

    @Test
    @DisplayName("Trap: LEFT JOIN + WHERE on the right table returns 4 of the 8 customers, and Postgres runs it as an Inner join (this documents the trap)")
    void aWhereOnTheRightTableDropsCustomers() {
        assertThat(jdbc.queryForObject("select count(*) from customers", Long.class)).as("customers in the shop").isEqualTo(8);

        assertThat(ids(rows(TRAP))).as("customers in the report").containsExactly(1L, 2L, 6L, 8L);

        Plan plan = plan(TRAP);
        assertThat(plan.joinTypes()).as("join type in the plan of a query that says LEFT JOIN: %s", plan).containsExactly("Inner");
    }
}
