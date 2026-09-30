package com.amigoscode.orders;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class OrderTrackerTest {

    static final Instant T0 = Instant.parse("2026-09-19T18:00:00Z");

    @Test
    void projectIsWiredUp() {
        assertThat(OrderTracker.class).isNotNull();
    }

    @Test
    @Disabled("story 1: remove this line to begin")
    void happyPathMovesThroughEveryState() {
        OrderTracker tracker = new OrderTracker(Clock.fixed(T0, ZoneOffset.UTC));
        OrderDetails order = new OrderDetails("r1", "berlin-mitte", T0, T0.plus(Duration.ofMinutes(30)));

        tracker.apply(new OrderEvent("e1", "o1", 1, OrderState.RECEIVED, T0, order));
        tracker.apply(new OrderEvent("e2", "o1", 2, OrderState.ACCEPTED, T0, order));
        tracker.apply(new OrderEvent("e3", "o1", 3, OrderState.PICKED_UP, T0, order));
        ApplyResult last = tracker.apply(new OrderEvent("e4", "o1", 4, OrderState.DELIVERED, T0, order));

        assertThat(last).isEqualTo(new ApplyResult.Applied(OrderState.DELIVERED));
        assertThat(tracker.stateOf("o1")).contains(OrderState.DELIVERED);
    }
}
