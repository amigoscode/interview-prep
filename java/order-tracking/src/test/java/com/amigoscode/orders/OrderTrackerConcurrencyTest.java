package com.amigoscode.orders;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.amigoscode.orders.OrderState.ACCEPTED;
import static com.amigoscode.orders.OrderState.CANCELLED;
import static com.amigoscode.orders.OrderState.DELIVERED;
import static com.amigoscode.orders.OrderState.PICKED_UP;
import static com.amigoscode.orders.OrderState.RECEIVED;
import static org.assertj.core.api.Assertions.assertThat;

/** Story 5: 8 consumer threads, many orders, every event delivered twice, in random order. */
class OrderTrackerConcurrencyTest {

    static final Instant NOW = Instant.parse("2026-09-19T18:00:00Z");
    static final int ORDERS = 2_000;
    static final int THREADS = 8;

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void stateStaysConsistentAndEachEventTakesEffectExactlyOnce() throws Exception {
        Clock frozen = Clock.fixed(NOW, ZoneOffset.UTC);
        OrderTracker tracker = new OrderTracker(frozen, ORDERS);

        List<OrderEvent> events = new ArrayList<>();
        for (int i = 0; i < ORDERS; i++) events.addAll(lifecycle(i));
        List<OrderEvent> stream = new ArrayList<>(events);
        stream.addAll(events);                                   // at-least-once: every event twice
        Collections.shuffle(stream, new Random(42));             // and in any order

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ApplyResult>> futures = new ArrayList<>();
        for (OrderEvent event : stream) {
            futures.add(pool.submit(() -> {
                start.await();
                return tracker.apply(event);
            }));
        }
        start.countDown();
        Map<String, Integer> nonDuplicates = new HashMap<>();
        int duplicates = 0;
        for (int i = 0; i < stream.size(); i++) {
            ApplyResult result = futures.get(i).get();
            if (result instanceof ApplyResult.Duplicate) duplicates++;
            else nonDuplicates.merge(stream.get(i).eventId(), 1, Integer::sum);
        }
        pool.shutdown();

        // Each eventId was processed exactly once; its twin was reported as a duplicate.
        assertThat(duplicates).isEqualTo(events.size());
        assertThat(nonDuplicates).hasSize(events.size()).allSatisfy((id, n) -> assertThat(n).isEqualTo(1));

        // Accept-the-latest: whatever the interleaving, every order ends in its last state.
        for (int i = 0; i < ORDERS; i++) {
            assertThat(tracker.stateOf("o" + i)).contains(i % 2 == 0 ? DELIVERED : CANCELLED);
        }

        // Every order counted once for its restaurant, however many threads saw it first.
        assertThat(tracker.topRestaurants(10)).containsExactly(
                new RestaurantCount("r0", ORDERS / 4), new RestaurantCount("r1", ORDERS / 4),
                new RestaurantCount("r2", ORDERS / 4), new RestaurantCount("r3", ORDERS / 4));

        // Every delivered order recorded exactly once: all took 25 minutes.
        assertThat(tracker.averageDeliveryTime()).contains(Duration.ofMinutes(25));
        assertThat(tracker.lateOrdersPerZone()).isEmpty();
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void racingFirstEventsForOneOrderCreateItOnce() throws Exception {
        Clock frozen = Clock.fixed(NOW, ZoneOffset.UTC);
        OrderTracker tracker = new OrderTracker(frozen);
        OrderDetails details = new OrderDetails("r1", "z1", NOW, NOW.plus(Duration.ofMinutes(30)));

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ApplyResult>> futures = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            OrderEvent received = new OrderEvent("same-event", "o1", 1, RECEIVED, NOW, details);
            futures.add(pool.submit(() -> { start.await(); return tracker.apply(received); }));
        }
        start.countDown();
        int applied = 0;
        for (Future<ApplyResult> f : futures) if (f.get() instanceof ApplyResult.Applied) applied++;
        pool.shutdown();

        assertThat(applied).isEqualTo(1);
        assertThat(tracker.topRestaurants(1)).containsExactly(new RestaurantCount("r1", 1));
    }

    /** Even orders are delivered after 25 minutes; odd orders are cancelled after acceptance. */
    private static List<OrderEvent> lifecycle(int i) {
        String orderId = "o" + i;
        Instant placedAt = NOW.minus(Duration.ofMinutes(30));
        OrderDetails details = new OrderDetails("r" + (i % 4), "z" + (i % 3), placedAt, placedAt.plus(Duration.ofMinutes(45)));
        List<OrderState> states = i % 2 == 0
                ? List.of(RECEIVED, ACCEPTED, PICKED_UP, DELIVERED)
                : List.of(RECEIVED, ACCEPTED, CANCELLED);
        List<OrderEvent> events = new ArrayList<>();
        for (int s = 0; s < states.size(); s++) {
            Instant at = placedAt.plus(Duration.ofMinutes(s == states.size() - 1 ? 25 : s * 5L));
            events.add(new OrderEvent(orderId + "-e" + s, orderId, s + 1, states.get(s), at, details));
        }
        return events;
    }
}
