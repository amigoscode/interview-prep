package com.amigoscode.idempotency.payment;

/**
 * What PaymentService tells the controller to answer: the HTTP status, the body, and whether this
 * is the stored answer of an earlier request with the same idempotency key.
 */
public record PaymentResult(int status, PaymentResponse body, boolean replayed) {

    /** A payment that was made just now: 201 Created. */
    public static PaymentResult created(PaymentResponse body) {
        return new PaymentResult(201, body, false);
    }

    /** The stored answer of an earlier request with the same key, sent again unchanged. */
    public static PaymentResult replay(int status, PaymentResponse body) {
        return new PaymentResult(status, body, true);
    }
}
