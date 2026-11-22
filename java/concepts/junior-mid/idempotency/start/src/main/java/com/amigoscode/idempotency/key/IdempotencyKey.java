package com.amigoscode.idempotency.key;

import java.util.regex.Pattern;

/**
 * Rules for the Idempotency-Key header. The client makes the key up (normally a UUID), once per
 * payment, and sends the same key again on every retry of that payment.
 */
public final class IdempotencyKey {

    public static final int MAX_LENGTH = 64;
    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9_-]+");

    private IdempotencyKey() {
    }

    /**
     * No header at all (null) is allowed: that request is simply not protected. A header that is
     * present must be 1 to 64 letters, digits, '-' or '_'.
     */
    public static void validate(String key) {
        if (key == null) {
            return;
        }
        if (key.isBlank()) {
            throw new InvalidIdempotencyKeyException("Idempotency-Key must not be blank");
        }
        if (key.length() > MAX_LENGTH) {
            throw new InvalidIdempotencyKeyException("Idempotency-Key must be at most " + MAX_LENGTH + " characters");
        }
        if (!ALLOWED.matcher(key).matches()) {
            throw new InvalidIdempotencyKeyException("Idempotency-Key may only contain letters, digits, '-' and '_'");
        }
    }
}
