package com.amigoscode.ratelimit;

/**
 * One client's bucket. Refill is computed lazily from the elapsed time, so there is no
 * background thread and nothing to schedule: the bucket is only ever touched by tryAcquire.
 * Mutable state is guarded by the bucket's own monitor, so clients never contend with each
 * other, only with themselves.
 */
final class TokenBucket {

    private final int capacity;
    private final double refillPerNano;
    private double tokens;        // guarded by this
    private long lastRefillNanos; // guarded by this

    TokenBucket(int capacity, double refillPerSecond, long nowNanos) {
        this.capacity = capacity;
        this.refillPerNano = refillPerSecond / 1_000_000_000.0;
        this.tokens = capacity;   // a new client starts with a full bucket
        this.lastRefillNanos = nowNanos;
    }

    synchronized boolean tryAcquire(long nowNanos) {
        refill(nowNanos);
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    private void refill(long nowNanos) {
        long elapsed = nowNanos - lastRefillNanos;
        if (elapsed <= 0) return;                       // clock did not move (or moved back)
        tokens = Math.min(capacity, tokens + elapsed * refillPerNano);
        lastRefillNanos = nowNanos;
    }
}
