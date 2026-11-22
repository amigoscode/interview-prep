package com.amigoscode.threadsafety;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.threadsafety.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The HTTP contract, ONE request at a time, against the real application. These pass from the
 * start and must keep passing: your fixes change how the beans hold their state, never what the
 * API answers.
 */
@Tag("baseline")
class ApiContractIT extends IntegrationTest {

    @Test
    @DisplayName("HTTP: three POST /api/visits, then GET /api/visits says 3")
    void visits() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(post("/api/visits").statusCode()).isEqualTo(200);
        }

        assertThat(get("/api/visits").body()).contains("\"visits\":3");
    }

    @Test
    @DisplayName("HTTP: two views of a page, then GET /api/pages/home/views says 2")
    void pageViews() throws Exception {
        post("/api/pages/home/views");
        post("/api/pages/home/views");

        assertThat(get("/api/pages/home/views").body()).contains("\"page\":\"home\"").contains("\"views\":2");
    }

    @Test
    @DisplayName("HTTP: GET /api/prices/ps5 twice returns the price twice and asks the supplier once")
    void prices() throws Exception {
        assertThat(get("/api/prices/ps5").body()).contains("\"product\":\"ps5\"").contains("499.00");
        assertThat(get("/api/prices/ps5").body()).contains("499.00");

        assertThat(priceSource.calls("ps5")).isEqualTo(1);
    }

    @Test
    @DisplayName("HTTP: GET /api/receipts returns the receipt of the customer in the request")
    void receipts() throws Exception {
        assertThat(get("/api/receipts?customer=alice&amountCents=2500").body())
                .contains("\"customer\":\"alice\"").contains("\"amountCents\":2500").contains("\"points\":25");
    }

    @Test
    @DisplayName("HTTP: GET /api/labels?shipDate=2026-03-05 returns 'Ships on 05 Mar 2026'")
    void labels() throws Exception {
        assertThat(get("/api/labels?shipDate=2026-03-05").body()).contains("\"label\":\"Ships on 05 Mar 2026\"");
    }

    @Test
    @DisplayName("HTTP: two sales of 5.00, then GET /api/sales/summary says 2 orders and 1000 cents")
    void sales() throws Exception {
        post("/api/sales?amountCents=500");
        post("/api/sales?amountCents=500");

        assertThat(get("/api/sales/summary").body()).contains("\"orders\":2").contains("\"revenueCents\":1000");
    }
}
