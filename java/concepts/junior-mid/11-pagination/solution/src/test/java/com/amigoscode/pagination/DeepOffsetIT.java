package com.amigoscode.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.pagination.db.Plan;
import com.amigoscode.pagination.order.OffsetPage;
import com.amigoscode.pagination.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Task 1: what OFFSET costs. These tests pass from the start and keep passing: OFFSET cannot be
 * made cheap, only replaced. They pin the problem down so you can see what keyset pagination changes.
 */
@Tag("task1")
class DeepOffsetIT extends IntegrationTest {

    @Test
    @DisplayName("Offset: the first page reads fewer than 100 rows to return 20")
    void theFirstPageIsCheap() {
        Recorded<OffsetPage> request = record(() -> orderService.page(0, 20));
        Plan plan = plan(request.pageQueries().getFirst());

        assertThat(request.result().items()).as("orders on page 0").hasSize(20);
        assertThat(plan.rowsRead()).as("rows read for page 0, with the plan: %s", plan).isLessThan(100);
    }

    @Test
    @DisplayName("Offset: page 500 reads more than 10,000 rows to return 20: every row of the 500 pages before it (this documents the problem)")
    void aDeepPageReadsEveryRowBeforeIt() {
        Recorded<OffsetPage> request = record(() -> orderService.page(500, 20));
        Plan plan = plan(request.pageQueries().getFirst());

        assertThat(request.result().items()).as("orders on page 500").hasSize(20);
        assertThat(request.pageQueries().getFirst().normalized()).as("the SQL of the page query").contains("offset");
        assertThat(plan.rowsRead())
                .as("rows read for page 500 (OFFSET 10000), with the plan: %s", plan)
                .isGreaterThanOrEqualTo(10_020);
    }
}
