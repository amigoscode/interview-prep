package com.amigoscode.ledger;

/** Outcome of an idempotent transfer. Replaying the key returns the same receipt. */
public record TransferReceipt(String idempotencyKey, boolean succeeded, String reason) {

    static TransferReceipt success(String key) {
        return new TransferReceipt(key, true, null);
    }

    static TransferReceipt failure(String key, String reason) {
        return new TransferReceipt(key, false, reason);
    }
}
