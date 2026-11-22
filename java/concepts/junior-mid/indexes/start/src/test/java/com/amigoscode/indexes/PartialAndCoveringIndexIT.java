package com.amigoscode.indexes;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.indexes.db.IndexInfo;
import com.amigoscode.indexes.db.Plan;
import com.amigoscode.indexes.shop.ShopQueries;
import com.amigoscode.indexes.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Task 8: an index on a small hot subset of the rows, and an index that answers a query on its own. */
@Tag("task8")
class PartialAndCoveringIndexIT extends IntegrationTest {

    @Test
    @DisplayName("Partial index: the pending queue is read from an index that only contains the PENDING orders")
    void pendingQueueUsesAPartialIndex() {
        Plan plan = plan(ShopQueries.pendingQueue());

        assertThat(plan.has("Seq Scan")).as("plan has a Seq Scan: %s", plan).isFalse();
        assertThat(plan.indexes()).as("indexes used by the plan").hasSize(1);
        IndexInfo index = index(plan.indexes().getFirst());
        assertThat(index.partial())
                .as("%s is a partial index (it has a WHERE clause). Its definition: %s", index.name(), index.definition())
                .isTrue();
    }

    @Test
    @DisplayName("Partial index: the queue reads exactly the 50 rows it returns, and nothing is sorted or thrown away")
    void pendingQueueReadsOnlyWhatItReturns() {
        Plan plan = plan(ShopQueries.pendingQueue());

        assertThat(plan.rowsReturned()).as("rows returned").isEqualTo(50);
        assertThat(plan.rowsRead()).as("rows read to return 50").isEqualTo(50);
        assertThat(plan.rowsThrownAway()).as("rows read and thrown away").isZero();
        assertThat(plan.has("Sort")).as("plan has a Sort step").isFalse();
    }

    @Test
    @DisplayName("Partial index: it is at least 20 times smaller than the primary key index, which covers every order")
    void thePartialIndexIsSmall() {
        Plan plan = plan(ShopQueries.pendingQueue());
        assertThat(plan.indexes()).as("indexes used by the plan").hasSize(1);
        IndexInfo index = index(plan.indexes().getFirst());

        assertThat(index.sizeBytes() * 20)
                .as("20 x the size of %s (%d bytes), compared with orders_pkey", index.name(), index.sizeBytes())
                .isLessThan(index("orders_pkey").sizeBytes());
    }

    @Test
    @DisplayName("Covering index: a customer's total spend is an Index Only Scan")
    void customerSpendIsAnIndexOnlyScan() {
        Plan plan = plan(ShopQueries.customerSpend(BIG_CUSTOMER));

        assertThat(plan.has("Index Only Scan"))
                .as("plan is an Index Only Scan. It is: %s", plan)
                .isTrue();
        assertThat(plan.rowsRead()).as("index entries read for a customer with 200 orders").isEqualTo(200);
    }

    @Test
    @DisplayName("Covering index: the table is never visited (Heap Fetches: 0)")
    void customerSpendNeverVisitsTheTable() {
        Plan plan = plan(ShopQueries.customerSpend(BIG_CUSTOMER));

        assertThat(plan.has("Index Only Scan")).as("plan is an Index Only Scan. It is: %s", plan).isTrue();
        assertThat(plan.heapFetches()).as("heap fetches").isZero();
    }
}
