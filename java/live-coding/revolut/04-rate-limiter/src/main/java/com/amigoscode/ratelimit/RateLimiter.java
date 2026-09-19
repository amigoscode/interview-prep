package com.amigoscode.ratelimit;

import java.time.Clock;

/** Story 1 starts here. The Clock is already in the constructor because story 2 needs it. */
public final class RateLimiter {

    public RateLimiter(int bucketSize, double refillPerSecond, Clock clock) {
        throw new UnsupportedOperationException("story 1");
    }

    public boolean tryAcquire(String clientId) {
        throw new UnsupportedOperationException("story 1");
    }
}
