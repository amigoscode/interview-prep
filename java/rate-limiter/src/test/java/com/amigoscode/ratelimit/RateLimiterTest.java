package com.amigoscode.ratelimit;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    @Test
    void projectIsWiredUp() {
        assertThat(RateLimiter.class).isNotNull();
    }

    @Test
    @Disabled("story 1: remove this line to begin")
    void allowsUpToBucketSizeThenRejects() {
        Clock fixed = Clock.fixed(Instant.parse("2026-09-19T10:00:00Z"), ZoneOffset.UTC);
        RateLimiter limiter = new RateLimiter(3, 1.0, fixed);
        assertThat(limiter.tryAcquire("alice")).isTrue();
        assertThat(limiter.tryAcquire("alice")).isTrue();
        assertThat(limiter.tryAcquire("alice")).isTrue();
        assertThat(limiter.tryAcquire("alice")).isFalse();
    }
}
