package com.amigoscode.orderapi.ratelimit;

import com.amigoscode.orderapi.MutableClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Story 5, without Spring. The deep version of this exercise is java/rate-limiter. */
class RateLimiterTest {

    final MutableClock clock = new MutableClock(Instant.parse("2026-09-30T12:00:00Z"));

    @Test
    void allowsCapacityThenRejectsWithHowLongToWait() {
        RateLimiter limiter = new RateLimiter(2, 0.5, clock);
        assertThat(limiter.tryAcquire("alice").allowed()).isTrue();
        assertThat(limiter.tryAcquire("alice").allowed()).isTrue();

        RateLimiter.Decision rejected = limiter.tryAcquire("alice");
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfter()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void refillsOverTimeButNeverBeyondCapacity() {
        RateLimiter limiter = new RateLimiter(2, 1.0, clock);
        limiter.tryAcquire("alice");
        limiter.tryAcquire("alice");

        clock.advance(Duration.ofMillis(500));
        RateLimiter.Decision halfway = limiter.tryAcquire("alice");
        assertThat(halfway.allowed()).isFalse();
        assertThat(halfway.retryAfter()).isEqualTo(Duration.ofMillis(500));

        clock.advance(Duration.ofMillis(500));
        assertThat(limiter.tryAcquire("alice").allowed()).isTrue();

        clock.advance(Duration.ofHours(1));
        assertThat(limiter.tryAcquire("alice").allowed()).isTrue();
        assertThat(limiter.tryAcquire("alice").allowed()).isTrue();
        assertThat(limiter.tryAcquire("alice").allowed()).isFalse();
    }

    @Test
    void keysAreIndependent() {
        RateLimiter limiter = new RateLimiter(1, 1.0, clock);
        assertThat(limiter.tryAcquire("alice").allowed()).isTrue();
        assertThat(limiter.tryAcquire("alice").allowed()).isFalse();
        assertThat(limiter.tryAcquire("bob").allowed()).isTrue();
    }

    @Test
    void clockGoingBackwardsDoesNotMintTokens() {
        RateLimiter limiter = new RateLimiter(1, 1.0, clock);
        limiter.tryAcquire("alice");
        clock.advance(Duration.ofSeconds(-5));
        assertThat(limiter.tryAcquire("alice").allowed()).isFalse();
    }

    @Test
    void rejectsBadConfiguration() {
        assertThatThrownBy(() -> new RateLimiter(0, 1, clock)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimiter(1, 0, clock)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimiter(1, 1, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void retryAfterHeaderRoundsUpToWholeSeconds() {
        assertThat(new RateLimitExceededException(Duration.ofMillis(1)).retryAfterSeconds()).isEqualTo(1);
        assertThat(new RateLimitExceededException(Duration.ofMillis(2001)).retryAfterSeconds()).isEqualTo(3);
        assertThat(new RateLimitExceededException(Duration.ofSeconds(2)).retryAfterSeconds()).isEqualTo(2);
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void exactlyCapacityRequestsSucceedUnderContention() throws Exception {
        RateLimiter limiter = new RateLimiter(50, 1.0, clock); // the clock is not moving

        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 1_000; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return limiter.tryAcquire("alice").allowed();
            }));
        }
        start.countDown();
        long allowed = 0;
        for (Future<Boolean> r : results) if (r.get()) allowed++;
        pool.shutdown();

        assertThat(allowed).isEqualTo(50);
    }
}
