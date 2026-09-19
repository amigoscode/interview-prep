package com.amigoscode.ledgerhttp;

import com.amigoscode.ledger.Ledger;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Story 3, part two, and story 4: the real ledger behind the real server. */
class LedgerAppEndToEndTest {

    Javalin app;
    HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void start() {
        app = new LedgerApp(new Ledger()).javalin().start(0);
    }

    @AfterEach
    void stop() {
        app.stop();
    }

    @Test
    void openTransferAndRead() throws Exception {
        assertThat(post("/accounts", "{\"id\":\"A\",\"openingBalance\":\"100.00\"}", null).statusCode()).isEqualTo(201);
        assertThat(post("/accounts", "{\"id\":\"B\",\"openingBalance\":\"0\"}", null).statusCode()).isEqualTo(201);
        assertThat(post("/transfers", "{\"from\":\"A\",\"to\":\"B\",\"amount\":\"30.00\"}", null).statusCode()).isEqualTo(200);

        assertThat(get("/accounts/A").body()).contains("\"balance\":\"70.00\"");
        assertThat(get("/accounts/B").body()).contains("\"balance\":\"30.00\"");
    }

    @Test
    void duplicateAccountIs400AndUnknownIs404() throws Exception {
        post("/accounts", "{\"id\":\"A\",\"openingBalance\":\"1\"}", null);
        assertThat(post("/accounts", "{\"id\":\"A\",\"openingBalance\":\"1\"}", null).statusCode()).isEqualTo(400);
        assertThat(get("/accounts/Z").statusCode()).isEqualTo(404);
    }

    @Test
    void idempotencyKeyStopsARetriedTransferFromMovingMoneyTwice() throws Exception {
        post("/accounts", "{\"id\":\"A\",\"openingBalance\":\"100.00\"}", null);
        post("/accounts", "{\"id\":\"B\",\"openingBalance\":\"0\"}", null);

        String body = "{\"from\":\"A\",\"to\":\"B\",\"amount\":\"30.00\"}";
        assertThat(post("/transfers", body, "req-42").statusCode()).isEqualTo(200);
        assertThat(post("/transfers", body, "req-42").statusCode()).isEqualTo(200); // the client retried
        assertThat(post("/transfers", body, "req-42").statusCode()).isEqualTo(200);

        assertThat(get("/accounts/A").body()).contains("\"balance\":\"70.00\"");
        assertThat(get("/accounts/B").body()).contains("\"balance\":\"30.00\"");
    }

    @Test
    void aFailedKeyedTransferReplaysItsFailure() throws Exception {
        post("/accounts", "{\"id\":\"A\",\"openingBalance\":\"10.00\"}", null);
        post("/accounts", "{\"id\":\"B\",\"openingBalance\":\"0\"}", null);

        String body = "{\"from\":\"A\",\"to\":\"B\",\"amount\":\"30.00\"}";
        assertThat(post("/transfers", body, "req-7").statusCode()).isEqualTo(422);
        post("/accounts", "{\"id\":\"C\",\"openingBalance\":\"0\"}", null);
        post("/transfers", "{\"from\":\"C\",\"to\":\"A\",\"amount\":\"0.01\"}", null);
        assertThat(post("/transfers", body, "req-7").statusCode()).isEqualTo(422); // still the first answer
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String json, String idempotencyKey) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json));
        if (idempotencyKey != null) b.header("Idempotency-Key", idempotencyKey);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + app.port() + path);
    }
}
