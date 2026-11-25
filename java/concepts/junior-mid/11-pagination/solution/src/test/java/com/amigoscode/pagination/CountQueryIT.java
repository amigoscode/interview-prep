package com.amigoscode.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.pagination.order.OffsetPage;
import com.amigoscode.pagination.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Task 8: a Spring Data Page counts every matching row on every request. Nobody asked for the total. */
@Tag("task8")
class CountQueryIT extends IntegrationTest {

    /** A business customer: 12 orders in the test database. */
    private static final long CUSTOMER = 1;

    @Test
    @DisplayName("Count: one page of GET /api/orders is exactly one SQL statement")
    void onePageIsOneStatement() {
        Recorded<OffsetPage> request = record(() -> orderService.page(3, 20));

        assertThat(request.statements())
                .as("SQL statements sent for GET /api/orders?page=3&size=20")
                .hasSize(1);
    }

    @Test
    @DisplayName("Count: no endpoint sends SELECT COUNT: not the order list, not a customer's history, not the feed")
    void nothingCountsTheTable() {
        assertThat(record(() -> orderService.page(3, 20)).countQueries())
                .as("COUNT statements sent for GET /api/orders").isEmpty();
        assertThat(record(() -> orderService.history(CUSTOMER, 0, 5)).countQueries())
                .as("COUNT statements sent for GET /api/customers/1/orders").isEmpty();
        assertThat(record(() -> orderService.feed(null, 20)).countQueries())
                .as("COUNT statements sent for GET /api/orders/feed").isEmpty();
    }

    @Test
    @DisplayName("Count: the page query asks for one row more than the page size, which is how it knows there is a next page")
    void thePageQueryFetchesOneRowMore() {
        Recorded<OffsetPage> request = record(() -> orderService.page(3, 20));

        assertThat(request.result().items()).as("orders returned to the client").hasSize(20);
        assertThat(request.pageQueries().getFirst().parameters())
                .as("values bound to the page query (the offset and the number of rows to fetch)")
                .containsExactly(60, 21);
    }

    @Test
    @DisplayName("Count: hasNext is still right without the total: true in the middle, false on the last page")
    void hasNextIsStillRight() {
        assertThat(orderService.page(0, 20).hasNext()).as("hasNext of page 0").isTrue();
        assertThat(orderService.page(ORDERS / 20 - 1, 20).hasNext()).as("hasNext of the last page").isFalse();
        assertThat(orderService.history(CUSTOMER, 1, 5).hasNext()).as("hasNext of page 1 of 3 of a customer").isTrue();
        assertThat(orderService.history(CUSTOMER, 2, 5).hasNext()).as("hasNext of page 2 of 3 of a customer").isFalse();
    }
}
