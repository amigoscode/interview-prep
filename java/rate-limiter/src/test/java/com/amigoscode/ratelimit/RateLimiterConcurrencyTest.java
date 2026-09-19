package com.amigoscode.ratelimit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Story 4. */
class RateLimiterConcurrencyTest {

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void exactlyBucketSizeCallsSucceedUnderContention() throws Exception {
        Clock frozen = Clock.fixed(Instant.parse("2026-09-19T10:00:00Z"), ZoneOffset.UTC);
        RateLimiter limiter = new RateLimiter(50, 0, frozen);

        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 1_000; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return limiter.tryAcquire("alice");
            }));
        }
        start.countDown();
        long allowed = 0;
        for (Future<Boolean> r : results) if (r.get()) allowed++;
        pool.shutdown();

        assertThat(allowed).isEqualTo(50);
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void firstCallsForANewClientCreateOneBucket() throws Exception {
        Clock frozen = Clock.fixed(Instant.parse("2026-09-19T10:00:00Z"), ZoneOffset.UTC);
        RateLimiter limiter = new RateLimiter(1, 0, frozen);

        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            results.add(pool.submit(() -> { start.await(); return limiter.tryAcquire("fresh"); }));
        }
        start.countDown();
        long allowed = 0;
        for (Future<Boolean> r : results) if (r.get()) allowed++;
        pool.shutdown();

        assertThat(allowed).isEqualTo(1); // two buckets would have allowed two
    }
}
