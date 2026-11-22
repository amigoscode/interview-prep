package com.amigoscode.restapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.restapi.support.Api;
import com.amigoscode.restapi.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** What works from the first minute. Whatever you change, these must stay green. */
@Tag("baseline")
class BaselineIT extends IntegrationTest {

    @Test
    @DisplayName("API: GET /api/orders returns all six orders")
    void listReturnsEveryOrder() {
        Api.Reply reply = api.get("/api/orders");

        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.json().size()).as("orders in the list").isEqualTo(6);
    }

    @Test
    @DisplayName("API: GET /api/orders?status=PENDING returns only the three pending orders")
    void listFiltersByStatus() {
        Api.Reply reply = api.get("/api/orders?status=PENDING");

        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.json().size()).as("pending orders").isEqualTo(3);
        reply.json().forEach(order -> assertThat(order.path("status").asString()).isEqualTo("PENDING"));
    }

    @Test
    @DisplayName("API: GET /api/orders/4 returns that order")
    void getReturnsOneOrder() {
        Api.Reply reply = api.get("/api/orders/4");

        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.json().path("id").asInt()).isEqualTo(4);
        assertThat(reply.json().path("customer").asString()).isEqualTo("Liam Brown");
        assertThat(reply.json().path("status").asString()).isEqualTo("PAID");
    }

    @Test
    @DisplayName("API: a valid POST stores a PENDING order, and the server works out the total")
    void createStoresTheOrder() {
        Api.Reply reply = api.post("/api/orders", VALID_ORDER);

        assertThat(reply.status()).as("a 2xx").isBetween(200, 299);
        assertThat(orderRows()).as("order rows").isEqualTo(7);
        assertThat(column(7, "customer")).isEqualTo("Nina Patel");
        assertThat(column(7, "status")).isEqualTo("PENDING");
        assertThat(column(7, "total")).as("2 x MOUSE at 20.00").isEqualTo("40.00");
    }

    @Test
    @DisplayName("API: reading orders never changes them")
    void readingChangesNothing() {
        List<Map<String, Object>> before = everyOrder();

        api.get("/api/orders");
        api.get("/api/orders?status=PAID");
        api.get("/api/orders/1");

        assertThat(everyOrder()).isEqualTo(before);
    }
}
