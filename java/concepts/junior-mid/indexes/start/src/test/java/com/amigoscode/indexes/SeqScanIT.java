package com.amigoscode.indexes;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.indexes.db.Plan;
import com.amigoscode.indexes.shop.ShopQueries;
import com.amigoscode.indexes.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Task 1. This test passes on purpose: it pins down the problem so you can see what an index changes. */
@Tag("task1")
class SeqScanIT extends IntegrationTest {

    @Test
    @DisplayName("No index: finding one order by its tracking number is a Seq Scan that reads all 200,000 rows (this documents the problem)")
    void withoutAnIndexEveryRowIsRead() {
        String trackingNumber = trackingNumberOf(123_456);

        Plan plan = thenRollBack(() -> {
            dropEveryIndexExceptThePrimaryKeys();
            return plan(ShopQueries.trackOrder(trackingNumber));
        });

        assertThat(plan.has("Seq Scan")).as("plan is a Seq Scan: %s", plan).isTrue();
        assertThat(plan.rowsReturned()).as("rows returned").isEqualTo(1);
        assertThat(plan.rowsRead()).as("rows read to return 1").isEqualTo(ORDERS);
    }
}
