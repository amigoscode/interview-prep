package com.amigoscode.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {

    MutableClock clock;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-09-19T10:00:00Z"));
    }

    @Nested
    class Story1FixedBudget {

        @Test
        void allowsUpToBucketSizeThenRejects() {
            RateLimiter limiter = new RateLimiter(3, 0, clock);
            assertThat(limiter.tryAcquire("alice")).isTrue();
            assertThat(limiter.tryAcquire("alice")).isTrue();
            assertThat(limiter.tryAcquire("alice")).isTrue();
            assertThat(limiter.tryAcquire("alice")).isFalse();
            assertThat(limiter.tryAcquire("alice")).isFalse();
        }

        @Test
        void rejectsBadConfiguration() {
            assertThatThrownBy(() -> new RateLimiter(0, 1, clock)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new RateLimiter(1, -1, clock)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new RateLimiter(1, 1, null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new RateLimiter(1, 1, clock).tryAcquire(" ")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Story2Refill {

        @Test
        void refillsAtTheConfiguredRate() {
            RateLimiter limiter = new RateLimiter(2, 1.0, clock); // 1 token per second
            assertThat(limiter.tryAcquire("alice")).isTrue();
            assertThat(limiter.tryAcquire("alice")).isTrue();
            assertThat(limiter.tryAcquire("alice")).isFalse();

            clock.advance(Duration.ofSeconds(1));
            assertThat(limiter.tryAcquire("alice")).isTrue();
            assertThat(limiter.tryAcquire("alice")).isFalse();
        }

        @Test
        void fractionalTimeGivesFractionalTokens() {
            RateLimiter limiter = new RateLimiter(2, 2.0, clock); // 2 per second
            limiter.tryAcquire("alice"); limiter.tryAcquire("alice");

            clock.advance(Duration.ofMillis(400));               // 0.8 token: not enough
            assertThat(limiter.tryAcquire("alice")).isFalse();
            clock.advance(Duration.ofMillis(100));               // now 1.0
            assertThat(limiter.tryAcquire("alice")).isTrue();
        }

        @Test
        void neverRefillsBeyondCapacity() {
            RateLimiter limiter = new RateLimiter(3, 100.0, clock);
            clock.advance(Duration.ofHours(1));
            assertThat(limiter.tryAcquire("alice")).isTrue();
            assertThat(limiter.tryAcquire("alice")).isTrue();
            assertThat(limiter.tryAcquire("alice")).isTrue();
            assertThat(limiter.tryAcquire("alice")).isFalse();
        }

        @Test
        void newClientStartsWithAFullBucket() {
            RateLimiter limiter = new RateLimiter(2, 0.0, clock);
            clock.advance(Duration.ofDays(1));
            assertThat(limiter.tryAcquire("late")).isTrue();
            assertThat(limiter.tryAcquire("late")).isTrue();
            assertThat(limiter.tryAcquire("late")).isFalse();
        }

        @Test
        void clockGoingBackwardsDoesNotMintTokens() {
            RateLimiter limiter = new RateLimiter(1, 1.0, clock);
            assertThat(limiter.tryAcquire("alice")).isTrue();
            clock.advance(Duration.ofSeconds(-5));
            assertThat(limiter.tryAcquire("alice")).isFalse();
        }
    }

    @Nested
    class Story3Isolation {

        @Test
        void clientsDoNotShareABucket() {
            RateLimiter limiter = new RateLimiter(1, 0, clock);
            assertThat(limiter.tryAcquire("alice")).isTrue();
            assertThat(limiter.tryAcquire("alice")).isFalse();
            assertThat(limiter.tryAcquire("bob")).isTrue();
        }
    }
}
