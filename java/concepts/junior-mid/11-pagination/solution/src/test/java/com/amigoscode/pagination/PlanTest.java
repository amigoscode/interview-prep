package com.amigoscode.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.pagination.db.Plan;
import com.amigoscode.pagination.db.SqlStatement;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit tests for the tools used by ./demo, ./inspect and the other tests: no Spring, no database. */
@Tag("baseline")
class PlanTest {

    private static final String DEEP_OFFSET = """
            [{"Plan": {"Node Type": "Limit", "Actual Rows": 20, "Actual Loops": 1,
                       "Shared Hit Blocks": 1000, "Shared Read Blocks": 11,
                       "Plans": [{"Node Type": "Index Scan", "Scan Direction": "Backward", "Relation Name": "orders",
                                  "Index Name": "idx_orders_created_at", "Actual Rows": 100000, "Actual Loops": 1,
                                  "Shared Hit Blocks": 1000, "Shared Read Blocks": 11}]},
              "Execution Time": 11.3}]
            """;

    private static final String COUNT = """
            [{"Plan": {"Node Type": "Aggregate", "Actual Rows": 1, "Actual Loops": 1,
                       "Shared Hit Blocks": 8334, "Shared Read Blocks": 0,
                       "Plans": [{"Node Type": "Seq Scan", "Relation Name": "orders",
                                  "Actual Rows": 1000000, "Actual Loops": 1,
                                  "Shared Hit Blocks": 8334, "Shared Read Blocks": 0}]},
              "Execution Time": 98.5}]
            """;

    @Test
    @DisplayName("Plan reader: a deep OFFSET is reported with the rows it read, the rows it returned and the pages it touched")
    void readsADeepOffset() {
        Plan plan = Plan.parse(DEEP_OFFSET);

        assertThat(plan.rowsReturned()).isEqualTo(20);
        assertThat(plan.rowsRead()).isEqualTo(100_000);
        assertThat(plan.pages()).isEqualTo(1_011);
        assertThat(plan.has("Sort")).isFalse();
        assertThat(plan.indexes()).containsExactly("idx_orders_created_at");
        assertThat(plan.describe()).isEqualTo("Index Scan Backward using idx_orders_created_at on orders");
    }

    @Test
    @DisplayName("Plan reader: a COUNT is reported with every row it had to read")
    void readsACount() {
        Plan plan = Plan.parse(COUNT);

        assertThat(plan.has("Seq Scan")).isTrue();
        assertThat(plan.rowsReturned()).isEqualTo(1);
        assertThat(plan.rowsRead()).isEqualTo(1_000_000);
    }

    @Test
    @DisplayName("SQL recorder: SELECT COUNT is told apart from the query that fetches the rows")
    void tellsACountFromAPageQuery() {
        SqlStatement count = new SqlStatement("select count(o1_0.id) from orders o1_0", List.of());
        SqlStatement page = new SqlStatement(
                "select o1_0.id,o1_0.created_at,o1_0.customer_id,o1_0.status,o1_0.total_cents from orders o1_0 "
                        + "order by o1_0.created_at desc offset ? rows fetch first ? rows only", List.of());

        assertThat(count.isCount()).isTrue();
        assertThat(page.isCount()).isFalse();
        assertThat(page.shortSql())
                .isEqualTo("select ... from orders o1_0 order by o1_0.created_at desc offset ? rows fetch first ? rows only");
    }
}
