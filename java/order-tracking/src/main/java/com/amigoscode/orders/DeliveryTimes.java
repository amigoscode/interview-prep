package com.amigoscode.orders;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/** Moving average over the last N delivery times, O(1) per delivery with a running sum. */
final class DeliveryTimes {

    private final int capacity;
    private final Deque<Duration> last = new ArrayDeque<>();
    private Duration sum = Duration.ZERO;

    DeliveryTimes(int capacity) {
        this.capacity = capacity;
    }

    synchronized void record(Duration deliveryTime) {
        last.addLast(deliveryTime);
        sum = sum.plus(deliveryTime);
        if (last.size() > capacity) sum = sum.minus(last.removeFirst());
    }

    synchronized Optional<Duration> average() {
        return last.isEmpty() ? Optional.empty() : Optional.of(sum.dividedBy(last.size()));
    }
}
