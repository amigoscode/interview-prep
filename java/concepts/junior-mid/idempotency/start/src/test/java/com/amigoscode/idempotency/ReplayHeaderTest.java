package com.amigoscode.idempotency;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.amigoscode.idempotency.payment.PaymentResponse;
import com.amigoscode.idempotency.payment.PaymentResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Unit tests: no Spring context, no database. PaymentService is a stub that returns what the test says. */
@Tag("task6")
class ReplayHeaderTest {

    private static final PaymentResponse PAYMENT = new PaymentResponse(42, 1, 2500, 7500);

    @Test
    @DisplayName("Replay header: a replayed answer has the stored status and body, plus the header Idempotent-Replay: true")
    void replayCarriesTheHeader() throws Exception {
        PaymentControllerTest.api(() -> PaymentResult.replay(201, PAYMENT))
                .perform(post("/api/payments").header("Idempotency-Key", PaymentControllerTest.KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(PaymentControllerTest.BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").value(42))
                .andExpect(jsonPath("$.balanceAfterCents").value(7500))
                .andExpect(header().string("Idempotent-Replay", "true"));
    }

    @Test
    @DisplayName("Replay header: the answer to the original request does not have the header")
    void originalDoesNotCarryTheHeader() throws Exception {
        PaymentControllerTest.api(() -> PaymentResult.created(PAYMENT))
                .perform(post("/api/payments").header("Idempotency-Key", PaymentControllerTest.KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(PaymentControllerTest.BODY))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replay"));
    }
}
