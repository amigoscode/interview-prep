package com.amigoscode.orderapi.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Per-key token bucket: {@code capacity} requests at once, refilled at {@code refillPerSecond}.
 * Refill is computed lazily from the injected clock, so tests move time instead of sleeping.
 * For the full exercise see java/rate-limiter in this repo.
 */
public final class RateLimiter {

    public record Decision(boolean allowed, Duration retryAfter) {

        static Decision allow() {
            return new Decision(true, Duration.ZERO);
        }
    }

    private final int capacity;
    private final double refillPerSecond;
    private final Clock clock;
    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(int capacity, double refillPerSecond, Clock clock) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        if (refillPerSecond <= 0) throw new IllegalArgumentException("refillPerSecond must be positive");
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Decision tryAcquire(String key) {
        return buckets.computeIfAbsent(key, k -> new Bucket(capacity, clock.instant())).tryTake();
    }

    private final class Bucket {

        private double tokens;
        private Instant lastRefill;

        Bucket(double tokens, Instant now) {
            this.tokens = tokens;
            this.lastRefill = now;
        }

        synchronized Decision tryTake() {
            Instant now = clock.instant();
            if (now.isAfter(lastRefill)) { // a clock going backwards must not mint tokens
                double elapsedSeconds = Duration.between(lastRefill, now).toNanos() / 1e9;
                tokens = Math.min(capacity, tokens + elapsedSeconds * refillPerSecond);
                lastRefill = now;
            }
            if (tokens >= 1) {
                tokens -= 1;
                return Decision.allow();
            }
            long waitNanos = (long) Math.ceil((1 - tokens) / refillPerSecond * 1e9);
            return new Decision(false, Duration.ofNanos(waitNanos));
        }
    }
}
