package com.amigoscode.restapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.restapi.support.Api;
import com.amigoscode.restapi.support.IntegrationTest;
import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("task5")
class ValidationIT extends IntegrationTest {

    @Test
    @DisplayName("Validation: an order for -5 keyboards returns 400 and nothing is saved")
    void negativeQuantityIsNotSaved() {
        Api.Reply reply = api.post("/api/orders", """
                {"customer": "Nina Patel", "product": "KEYBOARD", "quantity": -5, "shippingAddress": "8 Queen Square, Bath"}
                """);

        assertThat(reply.status()).isEqualTo(400);
        assertThat(orderRows()).as("order rows").isEqualTo(6);
    }

    @Test
    @DisplayName("Validation: the 400 is application/problem+json and \"errors\" lists every invalid field with a message")
    void everyInvalidFieldIsListed() {
        Api.Reply reply = api.post("/api/orders", """
                {"customer": "", "quantity": -5, "shippingAddress": "   "}
                """);

        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.contentType()).isEqualTo("application/problem+json");
        assertThat(reply.json().path("status").asInt()).as("status in the body").isEqualTo(400);
        assertThat(reply.invalidFields()).as("fields listed under errors")
                .containsExactlyInAnyOrder("customer", "product", "quantity", "shippingAddress");
        for (JsonNode error : reply.json().path("errors")) {
            assertThat(error.path("message").asString("")).as("message for " + error.path("field")).isNotBlank();
        }
        assertThat(orderRows()).as("order rows").isEqualTo(6);
    }

    @Test
    @DisplayName("Validation: the 400 does not show class names or the rejected Java object")
    void validationErrorDoesNotLeak() {
        Api.Reply reply = api.post("/api/orders", """
                {"customer": "", "product": "KEYBOARD", "quantity": -5, "shippingAddress": "8 Queen Square, Bath"}
                """);

        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.body().toLowerCase()).doesNotContain("exception", "java.", "com.amigoscode", "org.springframework", "trace");
    }
}
