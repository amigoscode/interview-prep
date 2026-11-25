package com.amigoscode.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.pagination.db.Plan;
import com.amigoscode.pagination.order.Cursor;
import com.amigoscode.pagination.order.FeedPage;
import com.amigoscode.pagination.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Task 5: keyset pagination. The page starts where the last one ended, instead of counting rows from the top. */
@Tag("task5")
class KeysetIT extends IntegrationTest {

    @Test
    @DisplayName("Keyset: indexes.sql creates an index on orders (created_at, id), the same columns as the ORDER BY")
    void theIndexMatchesTheSortOrder() {
        assertThat(indexedColumns())
                .as("indexes on orders. Add CREATE INDEX ... ON orders (created_at, id) to indexes.sql")
                .anyMatch(columns -> columns.startsWith("(created_at, id"));
    }

    @Test
    @DisplayName("Keyset: the feed query finds its starting point in the index with a row comparison, and sorts nothing")
    void theQueryIsARowComparisonOnTheIndex() {
        Cursor deep = cursorAfter(10_000);
        Recorded<FeedPage> request = record(() -> orderService.feed(deep, 20));
        assertThat(request.statements()).as("SQL statements for one page of the feed").hasSize(1);
        Plan plan = plan(request.statements().getFirst());

        assertThat(plan.has("Sort")).as("plan has a Sort step: %s", plan.nodes()).isFalse();
        assertThat(plan.scans()).as("steps of the plan that read the table").hasSize(1);
        assertThat(plan.scans().getFirst().condition())
                .as("Index Cond of the plan: the condition Postgres answers from the index. Plan: %s", plan)
                .isNotNull()
                .contains("ROW(created_at, id) < ROW(");
    }

    @Test
    @DisplayName("Keyset: a page 10,000 orders deep reads 21 rows, exactly as many as the first page")
    void aDeepPageCostsTheSameAsTheFirst() {
        Plan first = plan(record(() -> orderService.feed(null, 20)).statements().getFirst());
        Cursor cursor = cursorAfter(10_000);
        Plan deep = plan(record(() -> orderService.feed(cursor, 20)).statements().getFirst());

        assertThat(first.rowsRead()).as("rows read for the first page (20 orders + 1 to know if there are more)").isEqualTo(21);
        assertThat(deep.rowsRead()).as("rows read for the page after 10,000 orders, with the plan: %s", deep).isEqualTo(21);
    }

    @Test
    @DisplayName("Keyset: following nextCursor from the first page to the last returns all 20,000 orders exactly once, newest first")
    void followingTheCursorReturnsEveryOrderOnce() {
        List<Long> received = new ArrayList<>();
        FeedPage page = orderService.feed(null, 100);
        received.addAll(ids(page.items()));
        for (int request = 0; page.nextCursor() != null && request < 1_000; request++) {
            page = orderService.feed(Cursor.decode(page.nextCursor()), 100);
            received.addAll(ids(page.items()));
        }

        assertThat(received).as("number of orders received").hasSize(ORDERS);
        assertThat(received).as("ids received, in order").isEqualTo(expectedIds(0, ORDERS));
    }

    @Test
    @DisplayName("Keyset: hasNext is true while orders remain; the last page has hasNext = false and no nextCursor, even when it is exactly full")
    void theLastPageKnowsItIsTheLast() {
        FeedPage first = orderService.feed(null, 20);
        assertThat(first.items()).as("orders on the first page").hasSize(20);
        assertThat(first.hasNext()).as("hasNext of the first page").isTrue();
        assertThat(first.nextCursor()).as("nextCursor of the first page").isNotNull();

        // The 20 oldest orders: a full page, with nothing after it.
        FeedPage last = orderService.feed(cursorAfter(ORDERS - 20), 20);
        assertThat(ids(last.items())).as("the last page").isEqualTo(expectedIds(ORDERS - 20, 20));
        assertThat(last.hasNext()).as("hasNext of the last page").isFalse();
        assertThat(last.nextCursor()).as("nextCursor of the last page").isNull();
    }

    @Test
    @DisplayName("Keyset: an order placed between two requests changes nothing: the second page continues exactly where the first ended")
    void anInsertDoesNotShiftThePages() {
        thenRollBack(() -> {
            List<Long> expected = expectedIds(0, 40);
            FeedPage first = orderService.feed(null, 20);

            insertNewestOrder();
            FeedPage second = orderService.feed(Cursor.decode(first.nextCursor()), 20);

            assertThat(ids(first.items())).as("first page").isEqualTo(expected.subList(0, 20));
            assertThat(ids(second.items())).as("second page, read after the insert").isEqualTo(expected.subList(20, 40));
        });
    }
}
