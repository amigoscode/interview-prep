package com.amigoscode.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.pagination.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Task 4: OFFSET counts rows from the top of the list at the moment of each request. When the list
 * changes between two requests, the pages shift. These tests pass from the start and keep passing:
 * a tie-breaker does not fix this, only keyset pagination does (Task 5).
 */
@Tag("task4")
class OffsetShiftIT extends IntegrationTest {

    @Test
    @DisplayName("Offset: an order placed between two requests pushes the last order of page 0 onto page 1, so the client sees it twice (this documents the problem)")
    void anInsertRepeatsARow() {
        thenRollBack(() -> {
            List<Long> pageZero = ids(orderService.page(0, 20).items());

            insertNewestOrder();
            List<Long> pageOne = ids(orderService.page(1, 20).items());

            assertThat(pageOne.getFirst())
                    .as("first order of page 1, read after the insert")
                    .isEqualTo(pageZero.getLast());
        });
    }

    @Test
    @DisplayName("Offset: an order deleted between two requests pulls the first order of page 1 back onto page 0, so the client never sees it (this documents the problem)")
    void aDeleteSkipsARow() {
        thenRollBack(() -> {
            List<Long> pageZero = ids(orderService.page(0, 20).items());
            long nextInLine = ids(orderService.page(1, 20).items()).getFirst();

            jdbc.update("delete from orders where id = ?", pageZero.getFirst());
            List<Long> pageOne = ids(orderService.page(1, 20).items());

            assertThat(pageZero).as("page 0, read before the delete").doesNotContain(nextInLine);
            assertThat(pageOne).as("page 1, read after the delete").doesNotContain(nextInLine);
        });
    }
}
