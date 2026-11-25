package com.amigoscode.idempotency.key;

import com.amigoscode.idempotency.payment.PaymentRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * A fingerprint of the request body, stored next to the idempotency key. When the key comes back,
 * comparing fingerprints tells a real retry (same body) from a client that reused a key for a
 * different payment.
 */
public final class RequestHash {

    private RequestHash() {
    }

    /** SHA-256 of the fields that make two payments "the same request", as 64 hex characters. */
    public static String of(PaymentRequest request) {
        String canonical = "customerId=" + request.customerId() + ";amountCents=" + request.amountCents();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every JDK has SHA-256", e);
        }
    }
}
