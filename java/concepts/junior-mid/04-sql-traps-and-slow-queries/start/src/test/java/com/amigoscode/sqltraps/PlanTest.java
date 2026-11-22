package com.amigoscode.sqltraps;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.sqltraps.db.Plan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit tests for the plan reader used by ./demo, ./inspect and the other tests: no Spring, no database. */
@Tag("baseline")
class PlanTest {

    private static final String OUTER_JOIN = """
            [{"Plan": {"Node Type": "Hash Join", "Join Type": "Right", "Plan Rows": 8, "Plan Width": 22,
                       "Actual Rows": 13, "Actual Loops": 1, "Actual Total Time": 0.05,
                       "Shared Hit Blocks": 2, "Shared Read Blocks": 0,
                       "Plans": [
                         {"Node Type": "Seq Scan", "Parent Relationship": "Outer", "Relation Name": "orders",
                          "Plan Rows": 8, "Actual Rows": 8, "Actual Loops": 1, "Actual Total Time": 0.01,
                          "Filter": "(status = 'PAID'::text)", "Rows Removed by Filter": 4},
                         {"Node Type": "Hash", "Parent Relationship": "Inner", "Plan Rows": 8, "Actual Rows": 8,
                          "Actual Loops": 1, "Actual Total Time": 0.02,
                          "Plans": [{"Node Type": "Seq Scan", "Parent Relationship": "Outer", "Relation Name": "customers",
                                     "Plan Rows": 8, "Actual Rows": 8, "Actual Loops": 1, "Actual Total Time": 0.01}]}]},
              "Execution Time": 0.07}]
            """;

    private static final String SUBQUERY_PER_ROW = """
            [{"Plan": {"Node Type": "Seq Scan", "Relation Name": "customers", "Plan Rows": 50, "Plan Width": 38,
                       "Actual Rows": 50, "Actual Loops": 1, "Actual Total Time": 900.0,
                       "Rows Removed by Filter": 19958, "Shared Hit Blocks": 515000, "Shared Read Blocks": 0,
                       "Plans": [
                         {"Node Type": "Aggregate", "Parent Relationship": "SubPlan", "Subplan Name": "SubPlan 1",
                          "Plan Rows": 1, "Actual Rows": 1, "Actual Loops": 50, "Actual Total Time": 17.9,
                          "Plans": [{"Node Type": "Seq Scan", "Parent Relationship": "Outer", "Relation Name": "orders",
                                     "Plan Rows": 9, "Actual Rows": 6, "Actual Loops": 50, "Actual Total Time": 17.8,
                                     "Rows Removed by Filter": 300006}]}]},
              "Execution Time": 901.0}]
            """;

    private static final String SORT_AND_LIMIT = """
            [{"Plan": {"Node Type": "Limit", "Plan Rows": 20, "Plan Width": 230, "Actual Rows": 20, "Actual Loops": 1,
                       "Actual Total Time": 300.0, "Shared Hit Blocks": 10303, "Shared Read Blocks": 0,
                       "Plans": [{"Node Type": "Sort", "Parent Relationship": "Outer", "Plan Rows": 300012, "Actual Rows": 20,
                                  "Actual Loops": 1, "Actual Total Time": 299.0,
                                  "Sort Method": "external merge", "Sort Space Used": 71000, "Sort Space Type": "Disk",
                                  "Plans": [{"Node Type": "Seq Scan", "Parent Relationship": "Outer", "Relation Name": "orders",
                                             "Plan Rows": 1, "Actual Rows": 90000, "Actual Loops": 1,
                                             "Actual Total Time": 40.0}]}]},
              "Execution Time": 301.0}]
            """;

    @Test
    @DisplayName("Plan reader: the join type Postgres really used is read from the plan, with the rows each scan read")
    void readsTheJoinType() {
        Plan plan = Plan.parse(OUTER_JOIN);

        assertThat(plan.joinTypes()).containsExactly("Right");
        assertThat(plan.has("Hash Join")).isTrue();
        assertThat(plan.rowsReturned()).isEqualTo(13);
        assertThat(plan.rowsRead()).as("12 orders (8 kept, 4 removed) + 8 customers").isEqualTo(20);
        assertThat(plan.describe()).isEqualTo("Hash Join + Seq Scan on orders + Seq Scan on customers");
    }

    @Test
    @DisplayName("Plan reader: a subquery that runs once per row is found, and its rows are multiplied by its loops")
    void multipliesByLoops() {
        Plan plan = Plan.parse(SUBQUERY_PER_ROW);

        assertThat(plan.subPlans()).containsExactly("SubPlan 1");
        assertThat(plan.scanOf("orders").orElseThrow().loops()).isEqualTo(50);
        assertThat(plan.rowsRead()).as("20,008 customers + 50 x 300,012 orders").isEqualTo(20_008 + 50 * 300_012L);
        assertThat(plan.slowest().toString()).isEqualTo("Seq Scan on orders (SubPlan 1)");
    }

    @Test
    @DisplayName("Plan reader: a sort that spilled to disk and an estimate that is far off are reported")
    void readsSortsAndEstimates() {
        Plan plan = Plan.parse(SORT_AND_LIMIT);

        assertThat(plan.sorts()).hasSize(1);
        assertThat(plan.sorts().getFirst().sortOnDisk()).isTrue();
        assertThat(plan.sorts().getFirst().sortKilobytes()).isEqualTo(71_000);
        assertThat(plan.rowWidth()).isEqualTo(230);
        assertThat(plan.scanOf("orders").orElseThrow().estimateFactor()).isEqualTo(90_000.0);
        assertThat(plan.worstEstimate().type()).as("steps below a Limit are stopped early: not a wrong estimate")
                .isEqualTo("Limit");
    }
}
