package com.amigoscode.idempotency;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.amigoscode.idempotency.customer.CustomerNotFoundException;
import com.amigoscode.idempotency.key.IdempotencyKeyReusedException;
import com.amigoscode.idempotency.payment.InsufficientFundsException;
import com.amigoscode.idempotency.payment.PaymentController;
import com.amigoscode.idempotency.payment.PaymentRequest;
import com.amigoscode.idempotency.payment.PaymentResponse;
import com.amigoscode.idempotency.payment.PaymentResult;
import com.amigoscode.idempotency.payment.PaymentService;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Unit tests for the HTTP contract: no Spring context, no database, PaymentService is a stub. They
 * pass from the start and must keep passing, whatever you change in the service.
 */
@Tag("baseline")
class PaymentControllerTest {

    static final String BODY = "{\"customerId\":1,\"amountCents\":2500}";
    static final String KEY = "3f2a9c1e-7b1d-4c58-9a51-0d6f3f0e8a11";

    @Test
    @DisplayName("HTTP: a new payment returns 201 with the payment id and the balance after it")
    void newPaymentReturnsCreated() throws Exception {
        api(() -> PaymentResult.created(new PaymentResponse(42, 1, 2500, 7500)))
                .perform(post("/api/payments").header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").value(42))
                .andExpect(jsonPath("$.amountCents").value(2500))
                .andExpect(jsonPath("$.balanceAfterCents").value(7500));
    }

    @Test
    @DisplayName("HTTP: a request without an Idempotency-Key header is accepted (it is just not protected)")
    void keyIsOptional() throws Exception {
        api(() -> PaymentResult.created(new PaymentResponse(42, 1, 2500, 7500)))
                .perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("HTTP: a malformed Idempotency-Key returns 400 INVALID_IDEMPOTENCY_KEY")
    void malformedKeyReturnsBadRequest() throws Exception {
        api(() -> PaymentResult.created(new PaymentResponse(42, 1, 2500, 7500)))
                .perform(post("/api/payments").header("Idempotency-Key", "not a valid key!").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_IDEMPOTENCY_KEY"));
    }

    @Test
    @DisplayName("HTTP: a key that was already used for a different request returns 422 IDEMPOTENCY_KEY_REUSED")
    void reusedKeyReturnsUnprocessable() throws Exception {
        api(() -> {
            throw new IdempotencyKeyReusedException(KEY);
        }).perform(post("/api/payments").header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    @DisplayName("HTTP: not enough money returns 402 INSUFFICIENT_FUNDS")
    void insufficientFundsReturnsPaymentRequired() throws Exception {
        api(() -> {
            throw new InsufficientFundsException(1, 2500);
        }).perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().is(402))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    @DisplayName("HTTP: an unknown customer returns 404")
    void unknownCustomerReturnsNotFound() throws Exception {
        api(() -> {
            throw new CustomerNotFoundException(1);
        }).perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
    }

    /** The controller wired to a PaymentService whose pay does whatever the test says. */
    static MockMvc api(Supplier<PaymentResult> pay) {
        PaymentService stub = new PaymentService(null, null, null, null) {
            @Override
            public PaymentResult pay(String idempotencyKey, PaymentRequest request) {
                return pay.get();
            }
        };
        return MockMvcBuilders.standaloneSetup(new PaymentController(stub))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }
}
