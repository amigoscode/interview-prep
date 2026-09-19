package com.amigoscode.ledgerhttp;

import com.amigoscode.ledger.AccountNotFoundException;
import com.amigoscode.ledger.InsufficientFundsException;
import com.amigoscode.ledger.Ledger;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Story 3, part one: handlers against a mocked Ledger. This is the Mockito the interview asks
 * for: stub what the collaborator returns, verify what the handler passed to it, and never
 * test the ledger's rules twice.
 */
@ExtendWith(MockitoExtension.class)
class LedgerAppHandlerTest {

    @Mock Ledger ledger;
    Javalin app;
    HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void start() {
        app = new LedgerApp(ledger).javalin().start(0);
    }

    @AfterEach
    void stop() {
        app.stop();
    }

    @Test
    void getAccountReturnsBalanceFromLedger() throws Exception {
        when(ledger.balance("A")).thenReturn(new BigDecimal("100.00"));

        HttpResponse<String> res = get("/accounts/A");

        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).contains("\"id\":\"A\"").contains("\"balance\":\"100.00\"");
    }

    @Test
    void unknownAccountIs404() throws Exception {
        when(ledger.balance("nope")).thenThrow(new AccountNotFoundException("nope"));

        assertThat(get("/accounts/nope").statusCode()).isEqualTo(404);
    }

    @Test
    void openAccountCallsLedgerAndReturns201() throws Exception {
        when(ledger.balance("A")).thenReturn(new BigDecimal("100.00"));

        HttpResponse<String> res = post("/accounts", "{\"id\":\"A\",\"openingBalance\":\"100.00\"}");

        assertThat(res.statusCode()).isEqualTo(201);
        verify(ledger).open("A", new BigDecimal("100.00"));
    }

    @Test
    void transferPassesArgumentsThrough() throws Exception {
        HttpResponse<String> res = post("/transfers", "{\"from\":\"A\",\"to\":\"B\",\"amount\":\"30.00\"}");

        assertThat(res.statusCode()).isEqualTo(200);
        verify(ledger).transfer("A", "B", new BigDecimal("30.00"));
    }

    @Test
    void insufficientFundsIs422() throws Exception {
        doThrow(new InsufficientFundsException("A")).when(ledger).transfer(eq("A"), eq("B"), any());

        HttpResponse<String> res = post("/transfers", "{\"from\":\"A\",\"to\":\"B\",\"amount\":\"999\"}");

        assertThat(res.statusCode()).isEqualTo(422);
        assertThat(res.body()).contains("insufficient funds");
    }

    @Test
    void badAmountIs400() throws Exception {
        assertThat(post("/transfers", "{\"from\":\"A\",\"to\":\"B\",\"amount\":\"lots\"}").statusCode()).isEqualTo(400);
        assertThat(post("/transfers", "{\"from\":\"A\",\"to\":\"B\"}").statusCode()).isEqualTo(400);
    }

    @Test
    void ledgerRulesAreNotDuplicatedInTheHandler() throws Exception {
        // A negative amount reaches the ledger, which is where the rule lives; the handler only maps its answer.
        doThrow(new IllegalArgumentException("amount must be positive")).when(ledger).transfer(eq("A"), eq("B"), eq(new BigDecimal("-1")));

        assertThat(post("/transfers", "{\"from\":\"A\",\"to\":\"B\",\"amount\":\"-1\"}").statusCode()).isEqualTo(400);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String json) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + app.port() + path);
    }
}
