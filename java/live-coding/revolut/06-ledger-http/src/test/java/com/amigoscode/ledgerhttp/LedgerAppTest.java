package com.amigoscode.ledgerhttp;

import com.amigoscode.ledger.Ledger;
import io.javalin.Javalin;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class LedgerAppTest {

    @Mock Ledger ledger;

    @Test
    void appStartsAndStops() {
        Javalin app = new LedgerApp(ledger).javalin().start(0);
        try {
            assertThat(app.port()).isPositive();
        } finally {
            app.stop();
        }
    }

    @Test
    @Disabled("story 1: remove this line to begin")
    void unknownAccountIs404() throws Exception {
        Javalin app = new LedgerApp(new Ledger()).javalin().start(0);
        try {
            HttpResponse<String> res = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + "/accounts/nope")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            assertThat(res.statusCode()).isEqualTo(404);
        } finally {
            app.stop();
        }
    }
}
