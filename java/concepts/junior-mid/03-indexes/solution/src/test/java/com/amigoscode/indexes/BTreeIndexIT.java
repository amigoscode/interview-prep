package com.amigoscode.indexes;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.indexes.db.Plan;
import com.amigoscode.indexes.shop.ShopQueries;
import com.amigoscode.indexes.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Task 2: a B-tree index on the column in the WHERE clause. */
@Tag("task2")
class BTreeIndexIT extends IntegrationTest {

    @Test
    @DisplayName("B-tree: indexes.sql creates an index on orders (tracking_number)")
    void anIndexOnTrackingNumberExists() {
        assertThat(indexedColumns("orders"))
                .as("indexes on orders. Add CREATE INDEX ... ON orders (tracking_number) to indexes.sql")
                .anyMatch(columns -> columns.startsWith("(tracking_number"));
    }

    @Test
    @DisplayName("B-tree: finding an order by its tracking number is an Index Scan that reads 1 row, not a Seq Scan")
    void trackingAnOrderUsesTheIndex() {
        Plan plan = plan(ShopQueries.trackOrder(trackingNumberOf(123_456)));

        assertThat(plan.has("Seq Scan")).as("plan still has a Seq Scan: %s", plan).isFalse();
        assertThat(plan.indexes()).as("indexes used by the plan").isNotEmpty();
        assertThat(plan.rowsReturned()).as("rows returned").isEqualTo(1);
        assertThat(plan.rowsRead()).as("rows read to return 1").isEqualTo(1);
    }
}
