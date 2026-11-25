package com.amigoscode.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.pagination.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Task 3: ORDER BY created_at is not a complete order when many rows share a created_at. */
@Tag("task3")
class StableOrderIT extends IntegrationTest {

    private static final long CUSTOMER = 4_000_000;

    @Test
    @DisplayName("Order: the SQL sorts by created_at and then by id, so no two rows are ever equal")
    void theSqlHasATieBreaker() {
        String list = record(() -> orderService.page(0, 20)).pageQueries().getFirst().normalized();
        String history = record(() -> orderService.history(1, 0, 5)).pageQueries().getFirst().normalized();

        assertThat(list).as("ORDER BY of GET /api/orders")
                .containsPattern("order by \\S*created_at desc, ?\\S*\\bid desc");
        assertThat(history).as("ORDER BY of GET /api/customers/{id}/orders")
                .containsPattern("order by \\S*created_at desc, ?\\S*\\bid desc");
    }

    @Test
    @DisplayName("Order: orders with the same created_at come back with the highest id first, however they are stored")
    void tiesComeBackInOneFixedOrder() {
        thenRollBack(() -> {
            List<Long> expected = customerWithOrders(CUSTOMER, 120);

            assertThat(ids(orderService.history(CUSTOMER, 0, 20).items()))
                    .as("page 0 of a customer whose orders arrive a dozen at a time")
                    .containsExactlyElementsOf(expected.subList(0, 20));
            assertThat(ids(orderService.history(CUSTOMER, 2, 20).items()))
                    .as("page 2")
                    .containsExactlyElementsOf(expected.subList(40, 60));
        });
    }

    @Test
    @DisplayName("Order: reading a customer's 120 orders page by page returns each order exactly once")
    void walkingThePagesReturnsEveryOrderOnce() {
        thenRollBack(() -> {
            List<Long> expected = customerWithOrders(CUSTOMER, 120);

            List<Long> received = new ArrayList<>();
            for (int page = 0; page < 6; page++) {
                received.addAll(ids(orderService.history(CUSTOMER, page, 20).items()));
            }

            assertThat(received).as("ids received over 6 pages of 20. Nothing was inserted or deleted in between")
                    .doesNotHaveDuplicates()
                    .containsExactlyInAnyOrderElementsOf(expected);
        });
    }
}
