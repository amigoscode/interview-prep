package com.amigoscode.orders;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Orders per restaurant over a sliding window. A min-heap on placedAt finds what to evict even
 * when orders arrive out of order; a second, size-K heap picks the top restaurants.
 */
final class RecentOrders {

    private record Placement(Instant placedAt, String restaurantId) {}

    private static final Comparator<RestaurantCount> BUSIEST_FIRST =
            Comparator.comparingLong(RestaurantCount::orders).reversed()
                    .thenComparing(RestaurantCount::restaurantId);

    private final Duration window;
    private final PriorityQueue<Placement> byTime = new PriorityQueue<>(Comparator.comparing(Placement::placedAt));
    private final Map<String, Long> counts = new HashMap<>();

    RecentOrders(Duration window) {
        this.window = window;
    }

    synchronized void record(String restaurantId, Instant placedAt, Instant now) {
        if (!placedAt.isAfter(now.minus(window))) return; // already outside the window
        byTime.add(new Placement(placedAt, restaurantId));
        counts.merge(restaurantId, 1L, Long::sum);
    }

    synchronized List<RestaurantCount> top(int k, Instant now) {
        evictUpTo(now.minus(window));
        // Min-heap of size k: the root is the weakest of the current top k.
        PriorityQueue<RestaurantCount> heap = new PriorityQueue<>(k + 1, BUSIEST_FIRST.reversed());
        counts.forEach((restaurant, count) -> {
            heap.add(new RestaurantCount(restaurant, count));
            if (heap.size() > k) heap.poll();
        });
        return heap.stream().sorted(BUSIEST_FIRST).toList();
    }

    private void evictUpTo(Instant cutoff) {
        while (!byTime.isEmpty() && !byTime.peek().placedAt().isAfter(cutoff)) {
            Placement old = byTime.poll();
            counts.computeIfPresent(old.restaurantId(), (id, c) -> c == 1 ? null : c - 1);
        }
    }
}
