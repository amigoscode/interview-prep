package com.amigoscode.threadsafety;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.threadsafety.pricing.PriceCache;
import com.amigoscode.threadsafety.support.IntegrationTest;
import com.amigoscode.threadsafety.support.Meeting;
import com.amigoscode.threadsafety.support.Race;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Task 4, step 3. The supplier call sits between PriceCache's CHECK and its ACT. The stub holds
 * every thread there until all of them have arrived (see Meeting), so the bad interleaving is not
 * left to chance: with check-then-act code, every thread fetches. Every time.
 */
@Tag("task4")
class PriceCacheIT extends IntegrationTest {

    @Autowired
    private PriceCache cache;

    @Test
    @DisplayName("Price cache: 4 requests for the same product arrive together. The supplier is called once, and all 4 get the price")
    void theSupplierIsAskedOncePerProduct() {
        Meeting meeting = new Meeting(4);
        priceSource.everyoneFetchesAtTheSameMoment(meeting);
        BigDecimal[] prices = new BigDecimal[4];

        List<Throwable> thrown = Race.run(meeting, 4, 1, (thread, i) -> prices[thread] = cache.priceOf("ps5"));

        assertThat(Race.describe(thrown)).isEmpty();
        assertThat(priceSource.calls("ps5"))
                .withFailMessage("4 requests for the price of 'ps5' made %d calls to the supplier. Expected 1.",
                        priceSource.calls("ps5"))
                .isEqualTo(1);
        assertThat(prices).allSatisfy(price -> assertThat(price).isEqualByComparingTo("499.00"));
    }

    @Test
    @DisplayName("Price cache: 12 requests for 3 products arrive together. The supplier is called 3 times, once per product")
    void differentProductsAreFetchedOnceEach() {
        Meeting meeting = new Meeting(12);
        priceSource.everyoneFetchesAtTheSameMoment(meeting);
        String[] products = {"ps5", "xbox", "switch"};

        List<Throwable> thrown = Race.run(meeting, 12, 1, (thread, i) -> cache.priceOf(products[thread % 3]));

        assertThat(Race.describe(thrown)).isEmpty();
        assertThat(priceSource.totalCalls())
                .withFailMessage("12 requests for 3 products made %d calls to the supplier. Expected 3.",
                        priceSource.totalCalls())
                .isEqualTo(3);
        assertThat(priceSource.calls("ps5")).isEqualTo(1);
        assertThat(priceSource.calls("xbox")).isEqualTo(1);
        assertThat(priceSource.calls("switch")).isEqualTo(1);
    }
}
