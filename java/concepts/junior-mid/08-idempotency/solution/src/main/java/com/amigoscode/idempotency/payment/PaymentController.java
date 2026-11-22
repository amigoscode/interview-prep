package com.amigoscode.idempotency.payment;

import com.amigoscode.idempotency.key.IdempotencyKey;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String IDEMPOTENT_REPLAY = "Idempotent-Replay";

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> pay(
            @RequestHeader(value = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody PaymentRequest request) {
        if (request.amountCents() < 1) {
            throw new IllegalArgumentException("amountCents must be at least 1");
        }
        IdempotencyKey.validate(idempotencyKey);

        PaymentResult result = paymentService.pay(idempotencyKey, request);

        ResponseEntity.BodyBuilder response = ResponseEntity.status(result.status());
        if (result.replayed()) {
            // Same status and body as the original. The header is the only way to tell them apart.
            response.header(IDEMPOTENT_REPLAY, "true");
        }
        return response.body(result.body());
    }
}
