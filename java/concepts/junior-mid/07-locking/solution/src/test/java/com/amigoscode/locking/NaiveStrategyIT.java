package com.amigoscode.locking;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.locking.order.Strategy;
import com.amigoscode.locking.support.Buyers;
import com.amigoscode.locking.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Task 1. This test passes on purpose: it pins down the bug so you can see exactly what goes wrong. */
@Tag("task1")
class NaiveStrategyIT extends IntegrationTest {

    @Test
    @DisplayName("Naive: two buyers both get the last item, and the stock only goes down by one (this documents the bug)")
    void twoBuyersBothGetTheLastItem() {
        stockIs(1);
        fraudCheck.everyoneReadsBeforeAnyoneWrites(2);

        Buyers.Result result = Buyers.run(orderService, Strategy.NAIVE, 2);

        assertThat(result.confirmed()).as("orders confirmed for 1 item").isEqualTo(2);
        assertThat(orderRows()).as("order rows").isEqualTo(2);
        assertThat(stock()).as("stock after two sales of the same item").isEqualTo(0);
    }
}
