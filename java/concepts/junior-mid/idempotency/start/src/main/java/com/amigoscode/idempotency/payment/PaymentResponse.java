package com.amigoscode.idempotency.payment;

import tools.jackson.databind.json.JsonMapper;

/**
 * The body of a successful payment. {@code balanceAfterCents} is the customer's balance right
 * after THIS payment, so it cannot be worked out again later from the payment row alone.
 */
public record PaymentResponse(long paymentId, long customerId, long amountCents, long balanceAfterCents) {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** This response as JSON text, ready to be stored in idempotency_key.response_body. */
    public String toJson() {
        return JSON.writeValueAsString(this);
    }

    /** The reverse of {@link #toJson()}. */
    public static PaymentResponse fromJson(String json) {
        return JSON.readValue(json, PaymentResponse.class);
    }
}
