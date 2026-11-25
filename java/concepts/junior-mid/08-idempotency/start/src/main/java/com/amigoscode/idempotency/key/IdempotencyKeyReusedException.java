package com.amigoscode.idempotency.key;

/** The key was already used for a request with a different body. The API answers 422. */
public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException(String key) {
        super("Idempotency-Key " + key + " was already used for a different request. Use a new key for a new payment");
    }
}
