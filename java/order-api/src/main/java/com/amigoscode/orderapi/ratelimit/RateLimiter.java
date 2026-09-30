package com.amigoscode.orderapi.ratelimit;

import java.time.Clock;
import java.time.Duration;

/**
 * Story 5: a per-key token bucket. {@code capacity} requests at once, refilled at
 * {@code refillPerSecond}. Not a Spring bean yet: wire it up when you get there.
 */
public final class RateLimiter {

    /** Whether the call may proceed, and if not, how long until it could. */
    public record Decision(boolean allowed, Duration retryAfter) {
    }

    public RateLimiter(int capacity, double refillPerSecond, Clock clock) {
        throw new UnsupportedOperationException("story 5");
    }

    public Decision tryAcquire(String key) {
        throw new UnsupportedOperationException("story 5");
    }
}
