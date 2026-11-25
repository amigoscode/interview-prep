package com.amigoscode.sqltraps;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.sqltraps.db.Plan;
import com.amigoscode.sqltraps.shop.Query;
import com.amigoscode.sqltraps.shop.ShopQueries;
import com.amigoscode.sqltraps.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Task 4: "customers who never ordered" must survive a guest order whose customer_id is NULL. */
@Tag("task4")
class NeverOrderedIT extends IntegrationTest {

    /** The report as it was first written. It stays here for ever, so you can always compare against it. */
    private static final Query TRAP = new Query("""
            SELECT c.id, c.name
              FROM customers c
             WHERE c.id NOT IN (SELECT o.customer_id FROM orders o)
             ORDER BY c.id
            """);

    @BeforeEach
    void data() {
        smallShop();
    }

    @Test
    @DisplayName("Trap: NOT IN returns 0 rows because one guest order has a NULL customer_id, and 2 rows once that order is gone (this documents the trap)")
    void notInOnlyWorksWhileNoCustomerIdIsNull() {
        assertThat(ids(rows(TRAP))).as("NOT IN, with guest order 12 in the table").isEmpty();

        List<Long> withoutTheGuestOrder = thenRollBack(() -> {
            jdbc.update("delete from order_items where order_id = 12");
            jdbc.update("delete from orders where id = 12");
            return ids(rows(TRAP));
        });

        assertThat(withoutTheGuestOrder).as("NOT IN, without the guest order").containsExactly(4L, 7L);
    }

    @Test
    @DisplayName("Never ordered: the report finds Margaret (4) and Alan (7), although a guest order has a NULL customer_id")
    void findsTheTwoCustomersWithoutOrders() {
        assertThat(jdbc.queryForObject("select count(*) from orders where customer_id is null", Long.class))
                .as("guest orders in the shop").isEqualTo(1);

        assertThat(ids(rows(ShopQueries.customersWhoNeverOrdered())))
                .as("customers who never ordered")
                .containsExactly(4L, 7L);
    }

    @Test
    @DisplayName("Never ordered: Postgres runs it as an Anti join, which is what NOT EXISTS becomes")
    void postgresRunsAnAntiJoin() {
        Plan plan = plan(ShopQueries.customersWhoNeverOrdered());

        assertThat(plan.joinTypes()).as("join types in the plan: %s", plan).anyMatch(type -> type.contains("Anti"));
    }
}
