package com.amigoscode.ratelimit;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token-bucket rate limiter, one bucket per client.
 *
 * <p>Time comes from an injected {@link Clock}, so tests move it with a fake instead of
 * sleeping. Buckets are created with {@code computeIfAbsent}, so two first-time calls for the
 * same client cannot create two buckets. The follow-up question is what happens to this map
 * with millions of clients: evict idle buckets (a scheduled sweep or an LRU), or move the
 * bucket into Redis with a TTL so every instance of the service shares it.
 */
public final class RateLimiter {

    private final int bucketSize;
    private final double refillPerSecond;
    private final Clock clock;
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(int bucketSize, double refillPerSecond, Clock clock) {
        if (bucketSize <= 0) throw new IllegalArgumentException("bucket size must be positive");
        if (refillPerSecond < 0) throw new IllegalArgumentException("refill rate must not be negative");
        this.bucketSize = bucketSize;
        this.refillPerSecond = refillPerSecond;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public boolean tryAcquire(String clientId) {
        if (clientId == null || clientId.isBlank()) throw new IllegalArgumentException("client id must not be blank");
        long now = nanos(clock.instant());
        TokenBucket bucket = buckets.computeIfAbsent(clientId, id -> new TokenBucket(bucketSize, refillPerSecond, now));
        return bucket.tryAcquire(now);
    }

    private static long nanos(Instant instant) {
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }
}
