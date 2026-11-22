package com.amigoscode.locking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.amigoscode.locking.order.Strategy;
import com.amigoscode.locking.support.Buyers;
import com.amigoscode.locking.support.IntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("task2")
class PessimisticLockIT extends IntegrationTest {

    @Test
    @DisplayName("Pessimistic: two buyers, one item: exactly one order is confirmed, the other buyer is told it is sold out")
    void onlyOneBuyerGetsTheLastItem() {
        stockIs(1);
        fraudCheck.everyoneReadsBeforeAnyoneWrites(2);

        Buyers.Result result = Buyers.run(orderService, Strategy.PESSIMISTIC, 2);

        assertThat(result.unexpected()).as("errors that would be a 500").isEmpty();
        assertThat(result.confirmed()).as("orders confirmed for 1 item").isEqualTo(1);
        assertThat(result.soldOut()).as("buyers told it is sold out").isEqualTo(1);
        assertThat(orderRows()).as("order rows").isEqualTo(1);
        assertThat(stock()).as("stock").isEqualTo(0);
    }

    @Test
    @DisplayName("Pessimistic: the second buyer cannot even read the stock while the first buyer's transaction is open")
    void secondBuyerWaitsWhileTheFirstHoldsTheLock() throws InterruptedException {
        stockIs(1);
        fraudCheck.holdUntilReleased();

        Buyers race = Buyers.start(orderService, Strategy.PESSIMISTIC, 2);
        await().atMost(Duration.ofSeconds(10)).until(() -> fraudCheck.arrivals() >= 1);
        Thread.sleep(500); // plenty of time for the second buyer to read the stock, if nothing stops it

        assertThat(fraudCheck.arrivals())
                .as("buyers that got past reading the stock while the first transaction is still open")
                .isEqualTo(1);

        fraudCheck.release();
        Buyers.Result result = race.finish();
        assertThat(result.confirmed()).as("orders confirmed for 1 item").isEqualTo(1);
        assertThat(result.soldOut()).as("buyers told it is sold out").isEqualTo(1);
    }

    @Test
    @DisplayName("Pessimistic: 30 buyers, 10 items: exactly 10 orders, 20 sold out, stock ends at 0")
    void stockIsNeverOversoldUnderLoad() {
        stockIs(10);
        fraudCheck.takes(20);

        Buyers.Result result = Buyers.run(orderService, Strategy.PESSIMISTIC, 30);

        assertThat(result.unexpected()).as("errors that would be a 500").isEmpty();
        assertThat(result.confirmed()).as("orders confirmed for 10 items").isEqualTo(10);
        assertThat(result.soldOut()).as("buyers told it is sold out").isEqualTo(20);
        assertThat(orderRows()).as("order rows").isEqualTo(10);
        assertThat(stock()).as("stock").isEqualTo(0);
    }
}
