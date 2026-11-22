package com.amigoscode.restapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.restapi.support.Api;
import com.amigoscode.restapi.support.IntegrationTest;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("task10")
class StatusCodesIT extends IntegrationTest {

    @Test
    @DisplayName("Status: POST /api/orders returns 201 Created, with the new order in the body")
    void createReturnsCreated() {
        Api.Reply reply = api.post("/api/orders", VALID_ORDER);

        assertThat(reply.status()).isEqualTo(201);
        assertThat(reply.json().path("id").asInt()).as("id in the body").isEqualTo(7);
        assertThat(reply.json().path("status").asString()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Status: the 201 has a Location header, and a GET on it returns the new order")
    void locationPointsAtTheNewOrder() {
        Api.Reply created = api.post("/api/orders", VALID_ORDER);

        String location = created.header("Location");
        assertThat(location).as("Location header").isNotNull().endsWith("/api/orders/7");

        Api.Reply fetched = api.get(URI.create(location).getPath());
        assertThat(fetched.status()).as("GET " + location).isEqualTo(200);
        assertThat(fetched.json().path("customer").asString()).isEqualTo("Nina Patel");
    }

    @Test
    @DisplayName("Status: DELETE /api/orders/6 returns 204 with no body, and the order is gone")
    void deleteReturnsNoContent() {
        Api.Reply reply = api.delete("/api/orders/6");

        assertThat(reply.status()).isEqualTo(204);
        assertThat(reply.body()).as("body of a 204").isEmpty();
        assertThat(orderRows()).as("order rows").isEqualTo(5);
    }

    @Test
    @DisplayName("Status: deleting the same order twice returns 204, then 404, and no other order is deleted")
    void deletingTwice() {
        Api.Reply first = api.delete("/api/orders/6");
        Api.Reply second = api.delete("/api/orders/6");

        assertThat(first.status()).as("first DELETE").isEqualTo(204);
        assertThat(second.status()).as("second DELETE").isEqualTo(404);
        assertThat(second.contentType()).isEqualTo("application/problem+json");
        assertThat(orderRows()).as("order rows").isEqualTo(5);
    }
}
