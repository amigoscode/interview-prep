package com.amigoscode.locking;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.locking.order.Strategy;
import com.amigoscode.locking.support.Buyers;
import com.amigoscode.locking.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("task6")
class AtomicUpdateIT extends IntegrationTest {

    @Test
    @DisplayName("Atomic: two buyers, one item: exactly one order is confirmed, the other buyer is told it is sold out")
    void onlyOneBuyerGetsTheLastItem() {
        stockIs(1);
        fraudCheck.everyoneReadsBeforeAnyoneWrites(2);

        Buyers.Result result = Buyers.run(orderService, Strategy.ATOMIC, 2);

        assertThat(result.unexpected()).as("errors that would be a 500").isEmpty();
        assertThat(result.confirmed()).as("orders confirmed for 1 item").isEqualTo(1);
        assertThat(result.soldOut()).as("buyers told it is sold out").isEqualTo(1);
        assertThat(orderRows()).as("order rows").isEqualTo(1);
        assertThat(stock()).as("stock").isEqualTo(0);
    }

    @Test
    @DisplayName("Atomic: 30 buyers, 10 items: exactly 10 orders, 20 sold out, stock ends at 0, nobody gives up")
    void stockIsNeverOversoldUnderLoad() {
        stockIs(10);
        fraudCheck.takes(20);

        Buyers.Result result = Buyers.run(orderService, Strategy.ATOMIC, 30);

        assertThat(result.unexpected()).as("errors that would be a 500").isEmpty();
        assertThat(result.confirmed()).as("orders confirmed for 10 items").isEqualTo(10);
        assertThat(result.soldOut()).as("buyers told it is sold out").isEqualTo(20);
        assertThat(result.gaveUp()).as("buyers that gave up").isEqualTo(0);
        assertThat(orderRows()).as("order rows").isEqualTo(10);
        assertThat(stock()).as("stock").isEqualTo(0);
    }
}
