package com.amigoscode.idempotency.key;

import com.amigoscode.idempotency.payment.PaymentRequest;

/**
 * A fingerprint of the request body, stored next to the idempotency key. When the key comes back,
 * comparing fingerprints tells a real retry (same body) from a client that reused a key for a
 * different payment.
 */
public final class RequestHash {

    private RequestHash() {
    }

    /**
     * TODO Task 7: return the SHA-256 of the fields that make two payments "the same request"
     * (the customer id and the amount), as 64 hex characters.
     */
    public static String of(PaymentRequest request) {
        throw new UnsupportedOperationException("TODO Task 7: RequestHash.of is not written yet");
    }
}
