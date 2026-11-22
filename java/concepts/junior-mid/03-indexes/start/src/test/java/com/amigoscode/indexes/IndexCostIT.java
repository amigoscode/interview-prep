package com.amigoscode.indexes;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.indexes.db.Plan;
import com.amigoscode.indexes.db.WriteCost;
import com.amigoscode.indexes.shop.ShopQueries;
import com.amigoscode.indexes.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;

/**
 * Task 10. These tests pass from the start: they document what an index costs and when the planner
 * refuses to use one. They compare sizes and plans, never times.
 */
@Tag("task10")
class IndexCostIT extends IntegrationTest {

    @Test
    @DisplayName("Cost: six indexes on 20,000 orders take more disk space than the orders themselves")
    void indexesTakeMoreSpaceThanTheRows() {
        WriteCost.Result result = jdbc.execute((ConnectionCallback<WriteCost.Result>) connection -> WriteCost.run(connection, 20_000));

        assertThat(result.rows()).as("rows inserted into each table").isEqualTo(20_000);
        assertThat(result.indexes()).as("indexes on the indexed table").isEqualTo(6);
        assertThat(result.bareIndexBytes()).as("index bytes of the table with no index").isZero();
        assertThat(result.indexedIndexBytes())
                .as("bytes of the six indexes, compared with the %d bytes of rows", result.tableBytes())
                .isGreaterThan(result.tableBytes());
    }

    @Test
    @DisplayName("Cost: an index on status is NOT used for DELIVERED (93% of the rows): the planner prefers a Seq Scan")
    void aLowSelectivityValueIsASeqScan() {
        Plan plan = thenRollBack(() -> {
            jdbc.execute("create index idx_tmp_orders_status on orders (status)");
            return plan(ShopQueries.salesByStatus("DELIVERED"));
        });

        assertThat(plan.has("Seq Scan")).as("plan is a Seq Scan even though the index exists: %s", plan).isTrue();
    }

    @Test
    @DisplayName("Cost: the same index IS used for SHIPPED (1.6% of the rows)")
    void aSelectiveValueUsesTheIndex() {
        Plan plan = thenRollBack(() -> {
            jdbc.execute("create index idx_tmp_orders_status on orders (status)");
            return plan(ShopQueries.salesByStatus("SHIPPED"));
        });

        assertThat(plan.has("Seq Scan")).as("plan has a Seq Scan: %s", plan).isFalse();
        assertThat(plan.indexes()).as("indexes used by the plan").containsExactly("idx_tmp_orders_status");
    }
}
