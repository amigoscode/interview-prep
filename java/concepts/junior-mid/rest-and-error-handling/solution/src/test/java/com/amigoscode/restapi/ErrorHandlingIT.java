package com.amigoscode.restapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.restapi.support.Api;
import com.amigoscode.restapi.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("task4")
class ErrorHandlingIT extends IntegrationTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    @Test
    @DisplayName("Errors: GET /api/orders/999 returns 404 as application/problem+json with title, status and detail")
    void missingOrderIsNotFound() {
        Api.Reply reply = api.get("/api/orders/999");

        assertThat(reply.status()).isEqualTo(404);
        assertThat(reply.contentType()).isEqualTo(PROBLEM_JSON);
        assertThat(reply.json().path("status").asInt()).as("status in the body").isEqualTo(404);
        assertThat(reply.json().path("title").asString("")).as("title").isNotBlank();
        assertThat(reply.json().path("detail").asString("")).as("detail").contains("999");
    }

    @Test
    @DisplayName("Errors: a body that is not JSON returns 400 as application/problem+json, and nothing is saved")
    void brokenJsonIsBadRequest() {
        Api.Reply reply = api.post("/api/orders", "{\"customer\": \"Nina Patel\", \"product\": ");

        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.contentType()).isEqualTo(PROBLEM_JSON);
        assertThat(orderRows()).as("order rows").isEqualTo(6);
    }

    @Test
    @DisplayName("Errors: a product that does not exist (\"PLAYSTATION\") returns 400, and nothing is saved")
    void unknownProductIsBadRequest() {
        Api.Reply reply = api.post("/api/orders", VALID_ORDER.replace("MOUSE", "PLAYSTATION"));

        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.contentType()).isEqualTo(PROBLEM_JSON);
        assertThat(orderRows()).as("order rows").isEqualTo(6);
    }

    @Test
    @DisplayName("Errors: GET /api/orders?status=DONE returns 400, because DONE is not a status")
    void unknownStatusFilterIsBadRequest() {
        Api.Reply reply = api.get("/api/orders?status=DONE");

        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.contentType()).isEqualTo(PROBLEM_JSON);
    }

    @Test
    @DisplayName("Errors: cancelling a shipped order returns 409 as application/problem+json, and it stays SHIPPED")
    void cancellingShippedIsConflict() {
        Api.Reply reply = api.send("POST", "/api/orders/5/cancel", null);

        assertThat(reply.status()).isEqualTo(409);
        assertThat(reply.contentType()).isEqualTo(PROBLEM_JSON);
        assertThat(reply.json().path("detail").asString("")).as("detail").contains("SHIPPED");
        assertThat(statusOf(5)).isEqualTo("SHIPPED");
    }

    @Test
    @DisplayName("Errors: PUT /api/orders/999 returns 404 and creates nothing")
    void putToMissingOrderIsNotFound() {
        Api.Reply reply = api.put("/api/orders/999", VALID_ORDER);

        assertThat(reply.status()).isEqualTo(404);
        assertThat(orderRows()).as("order rows").isEqualTo(6);
    }

    @Test
    @DisplayName("Errors: DELETE /api/orders/999 returns 404 and deletes nothing")
    void deleteOfMissingOrderIsNotFound() {
        Api.Reply reply = api.delete("/api/orders/999");

        assertThat(reply.status()).isEqualTo(404);
        assertThat(orderRows()).as("order rows").isEqualTo(6);
    }

    @Test
    @DisplayName("Errors: DELETE /api/orders (a method that URL does not have) returns 405 with an Allow header")
    void wrongMethodIsMethodNotAllowed() {
        Api.Reply reply = api.delete("/api/orders");

        assertThat(reply.status()).isEqualTo(405);
        assertThat(reply.header("Allow")).as("Allow header").contains("GET").contains("POST");
    }

    @Test
    @DisplayName("Errors: no error body shows a class name, a stack trace or SQL")
    void errorBodiesDoNotLeak() {
        List<Api.Reply> replies = List.of(
                api.get("/api/orders/999"),
                api.get("/api/orders/abc"),
                api.get("/api/orders?status=DONE"),
                api.get("/api/nope"),
                api.delete("/api/orders"),
                api.post("/api/orders", "{\"customer\": "),
                api.post("/api/orders", VALID_ORDER.replace("MOUSE", "PLAYSTATION")),
                api.send("POST", "/api/orders/5/cancel", null));

        for (Api.Reply reply : replies) {
            String request = reply.raw().request().method() + " " + reply.raw().uri().getPath();
            assertThat(reply.status()).as("status of " + request).isBetween(400, 499);
            assertThat(reply.body().toLowerCase()).as("error body of " + request)
                    .doesNotContain("exception", "java.", "com.amigoscode", "org.springframework", "trace", "insert into");
        }
    }
}
