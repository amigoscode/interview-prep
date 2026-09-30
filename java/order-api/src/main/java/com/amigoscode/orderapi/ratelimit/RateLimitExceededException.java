package com.amigoscode.orderapi.ratelimit;

import java.time.Duration;

public class RateLimitExceededException extends RuntimeException {

    private final Duration retryAfter;

    public RateLimitExceededException(Duration retryAfter) {
        super("Too many orders; retry in " + retryAfter.toMillis() + " ms");
        this.retryAfter = retryAfter;
    }

    /** Whole seconds for the {@code Retry-After} header, rounded up and never 0. */
    public long retryAfterSeconds() {
        long seconds = retryAfter.toSeconds() + (retryAfter.toNanosPart() > 0 ? 1 : 0);
        return Math.max(1, seconds);
    }
}
