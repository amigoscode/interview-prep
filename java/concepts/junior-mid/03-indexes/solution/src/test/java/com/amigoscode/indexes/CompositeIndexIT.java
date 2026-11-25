package com.amigoscode.indexes;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.indexes.db.Plan;
import com.amigoscode.indexes.shop.ShopQueries;
import com.amigoscode.indexes.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Task 4: a composite index, and why the order of its columns matters. */
@Tag("task4")
class CompositeIndexIT extends IntegrationTest {

    @Test
    @DisplayName("Composite: indexes.sql creates an index on orders (customer_id, created_at), in that column order")
    void aCompositeIndexInTheRightOrderExists() {
        assertThat(indexedColumns("orders"))
                .as("indexes on orders. The equality column (customer_id) must come first, then the sort column (created_at)")
                .anyMatch(columns -> columns.startsWith("(customer_id, created_at"));
    }

    @Test
    @DisplayName("Composite: a customer's 5 newest orders are found by reading fewer than 100 pages, not by walking an index from the end")
    void latestOrdersTouchFewPages() {
        Plan plan = plan(ShopQueries.latestOrders(TYPICAL_CUSTOMER));

        assertThat(plan.has("Seq Scan")).as("plan has a Seq Scan: %s", plan).isFalse();
        assertThat(plan.rowsReturned()).as("rows returned").isEqualTo(5);
        assertThat(plan.pages())
                .as("8 kB pages read to return 5 rows, with the plan: %s", plan)
                .isLessThan(100);
    }

    @Test
    @DisplayName("Composite: the rows come out of the index already newest first: 5 rows read, no Sort step")
    void latestOrdersNeedNoSort() {
        Plan plan = plan(ShopQueries.latestOrders(BIG_CUSTOMER));

        assertThat(plan.indexes()).as("indexes used by the plan").hasSize(1);
        assertThat(index(plan.indexes().getFirst()).columns())
                .as("columns of %s, the index the plan uses", plan.indexes().getFirst())
                .startsWith("(customer_id, created_at");
        assertThat(plan.has("Sort")).as("plan has a Sort step").isFalse();
        assertThat(plan.rowsRead()).as("rows read to return the 5 newest of 200 orders").isEqualTo(5);
    }
}
