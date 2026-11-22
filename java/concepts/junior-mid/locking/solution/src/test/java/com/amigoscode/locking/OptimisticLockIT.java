package com.amigoscode.locking;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.locking.order.Strategy;
import com.amigoscode.locking.support.Buyers;
import com.amigoscode.locking.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("task4")
class OptimisticLockIT extends IntegrationTest {

    @Test
    @DisplayName("Optimistic: every sale raises the product's version by exactly one")
    void versionGoesUpByOneOnEverySale() {
        stockIs(5);

        for (int sale = 0; sale < 3; sale++) {
            orderService.placeOrder(Strategy.OPTIMISTIC, Buyers.PRODUCT_ID, 1);
        }

        assertThat(stock()).as("stock after 3 sales").isEqualTo(2);
        assertThat(version()).as("version after 3 sales, starting from 0").isEqualTo(3);
    }

    @Test
    @DisplayName("Optimistic: two buyers, one item: exactly one order is confirmed, the loser retries and is told it is sold out")
    void onlyOneBuyerGetsTheLastItem() {
        stockIs(1);
        fraudCheck.everyoneReadsBeforeAnyoneWrites(2);

        Buyers.Result result = Buyers.run(orderService, Strategy.OPTIMISTIC, 2);

        assertThat(result.unexpected()).as("errors that would be a 500").isEmpty();
        assertThat(result.confirmed()).as("orders confirmed for 1 item").isEqualTo(1);
        assertThat(result.soldOut()).as("buyers told it is sold out").isEqualTo(1);
        assertThat(orderRows()).as("order rows").isEqualTo(1);
        assertThat(stock()).as("stock").isEqualTo(0);
    }

    @Test
    @DisplayName("Optimistic: two buyers, two items: the loser retries and still gets one, and no stock update is lost")
    void theLoserRetriesAndBuysWhenStockRemains() {
        stockIs(2);
        fraudCheck.everyoneReadsBeforeAnyoneWrites(2);

        Buyers.Result result = Buyers.run(orderService, Strategy.OPTIMISTIC, 2);

        assertThat(result.unexpected()).as("errors that would be a 500").isEmpty();
        assertThat(result.confirmed()).as("orders confirmed for 2 items").isEqualTo(2);
        assertThat(orderRows()).as("order rows").isEqualTo(2);
        assertThat(stock()).as("stock after 2 sales, starting from 2").isEqualTo(0);
    }

    @Test
    @DisplayName("Optimistic: 20 buyers, 5 items: never oversold, and stock left + orders placed always equals 5")
    void stockAndOrdersAlwaysAddUpUnderLoad() {
        stockIs(5);
        fraudCheck.takes(10);

        Buyers.Result result = Buyers.run(orderService, Strategy.OPTIMISTIC, 20);

        assertThat(result.unexpected()).as("errors that would be a 500").isEmpty();
        assertThat(result.confirmed() + result.soldOut() + result.gaveUp()).as("buyers accounted for").isEqualTo(20);
        assertThat(result.confirmed()).as("orders confirmed for 5 items").isBetween(1, 5);
        assertThat(orderRows()).as("order rows").isEqualTo(result.confirmed());
        assertThat(stock()).as("stock left").isEqualTo(5 - result.confirmed());
    }
}
