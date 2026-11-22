package com.amigoscode.sqltraps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.amigoscode.sqltraps.shop.ShopQueries;
import com.amigoscode.sqltraps.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Task 5: "spent more than X in total" is a condition on the group, not on one order. */
@Tag("task5")
class BigSpendersIT extends IntegrationTest {

    @BeforeEach
    void data() {
        smallShop();
    }

    @Test
    @DisplayName("Big spenders: Barbara is found: three orders of 40.00 add up to more than 100.00")
    void findsEveryCustomerWhoseTotalIsOverTheLimit() {
        assertThat(ids(rows(ShopQueries.bigSpenders(10_000)))).as("customers who spent more than 100.00").containsExactly(1L, 6L);
        assertThat(ids(rows(ShopQueries.bigSpenders(9_000)))).as("more than 90.00: exactly 90.00 is not more").containsExactly(1L, 6L);
        assertThat(ids(rows(ShopQueries.bigSpenders(8_999)))).as("more than 89.99").containsExactly(1L, 2L, 6L, 8L);
    }

    @Test
    @DisplayName("Big spenders: the amount shown is everything the customer spent: Ada 180.00, not only her 150.00 order")
    void showsTheFullAmountSpent() {
        assertThat(byId(rows(ShopQueries.bigSpenders(10_000)), "spent_cents"))
                .as("spent_cents by customer id")
                .containsExactly(entry(1L, 18_000L), entry(6L, 12_000L));
    }
}
