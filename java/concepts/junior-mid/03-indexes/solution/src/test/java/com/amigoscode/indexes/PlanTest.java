package com.amigoscode.indexes;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.indexes.db.Plan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit tests for the plan reader used by ./demo and by the other tests: no Spring, no database. */
@Tag("baseline")
class PlanTest {

    private static final String SEQ_SCAN = """
            [{"Plan": {"Node Type": "Seq Scan", "Relation Name": "orders", "Actual Rows": 1, "Actual Loops": 1,
                       "Filter": "(tracking_number = 'TRK1'::text)", "Rows Removed by Filter": 999999,
                       "Shared Hit Blocks": 10000, "Shared Read Blocks": 310},
              "Execution Time": 65.1}]
            """;

    private static final String INDEX_ONLY_SCAN = """
            [{"Plan": {"Node Type": "Aggregate", "Actual Rows": 1, "Actual Loops": 1,
                       "Shared Hit Blocks": 7, "Shared Read Blocks": 0,
                       "Plans": [{"Node Type": "Index Only Scan", "Relation Name": "orders",
                                  "Index Name": "idx_orders_customer_created", "Index Cond": "(customer_id = 1)",
                                  "Actual Rows": 1000, "Actual Loops": 1, "Heap Fetches": 0,
                                  "Shared Hit Blocks": 7, "Shared Read Blocks": 0}]},
              "Execution Time": 0.2}]
            """;

    @Test
    @DisplayName("Plan reader: a Seq Scan is reported with the rows it read and the rows it threw away")
    void readsASeqScan() {
        Plan plan = Plan.parse(SEQ_SCAN);

        assertThat(plan.has("Seq Scan")).isTrue();
        assertThat(plan.indexes()).isEmpty();
        assertThat(plan.rowsReturned()).isEqualTo(1);
        assertThat(plan.rowsRead()).isEqualTo(1_000_000);
        assertThat(plan.rowsThrownAway()).isEqualTo(999_999);
        assertThat(plan.pages()).isEqualTo(10_310);
    }

    @Test
    @DisplayName("Plan reader: nested steps are found, with the index they use and their heap fetches")
    void readsNestedSteps() {
        Plan plan = Plan.parse(INDEX_ONLY_SCAN);

        assertThat(plan.has("Seq Scan")).isFalse();
        assertThat(plan.has("Index Only Scan")).isTrue();
        assertThat(plan.indexes()).containsExactly("idx_orders_customer_created");
        assertThat(plan.rowsReturned()).isEqualTo(1);
        assertThat(plan.rowsRead()).isEqualTo(1000);
        assertThat(plan.heapFetches()).isZero();
        assertThat(plan.describe()).isEqualTo("Index Only Scan using idx_orders_customer_created on orders");
    }
}
