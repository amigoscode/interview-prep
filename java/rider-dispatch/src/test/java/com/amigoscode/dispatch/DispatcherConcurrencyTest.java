package com.amigoscode.dispatch;

import com.amigoscode.dispatch.AssignmentResult.Assigned;
import com.amigoscode.dispatch.AssignmentResult.NoRiderAvailable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Story 4. */
class DispatcherConcurrencyTest {

    static final Location RESTAURANT = new Location(52.5200, 13.4050);
    static final Clock NOON = Clock.fixed(Instant.parse("2026-09-30T12:00:00Z"), ZoneOffset.UTC);
    static final int THREADS = 8;

    /** Riders on a line north of the restaurant, all on shift all day. */
    static Dispatcher dispatcherWithRiders(int count) {
        ShiftSchedule schedule = new ShiftSchedule();
        List<Rider> riders = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Rider rider = new Rider("r" + i, new Location(52.5200 + i * 0.001, 13.4050));
            riders.add(rider);
            schedule.addShift(rider.id(), LocalTime.MIN, LocalTime.MAX);
        }
        return new Dispatcher(riders, schedule, NOON);
    }

    /** Submits every order at once behind a start gate and returns the results. */
    static List<AssignmentResult> assignConcurrently(Dispatcher dispatcher, int orders) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<AssignmentResult>> futures = new ArrayList<>();
            for (int i = 0; i < orders; i++) {
                Order order = new Order("o" + i, RESTAURANT);
                futures.add(pool.submit(() -> {
                    start.await();
                    return dispatcher.assign(order);
                }));
            }
            start.countDown();
            List<AssignmentResult> results = new ArrayList<>();
            for (Future<AssignmentResult> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void thousandOrdersAgainstFiftyRidersAssignExactlyFiftyToDistinctRiders() throws Exception {
        Dispatcher dispatcher = dispatcherWithRiders(50);

        List<AssignmentResult> results = assignConcurrently(dispatcher, 1_000);

        List<Assigned> assigned = results.stream()
                .filter(Assigned.class::isInstance).map(Assigned.class::cast).toList();
        assertThat(assigned).hasSize(50);
        assertThat(assigned.stream().map(a -> a.rider().id()).distinct()).hasSize(50);
        assertThat(results.stream().filter(NoRiderAvailable.class::isInstance)).hasSize(950);
        for (Assigned a : assigned) {
            assertThat(dispatcher.riderFor(a.order().id())).contains(a.rider());
        }
        for (int i = 0; i < 50; i++) {
            assertThat(dispatcher.isBusy("r" + i)).isTrue();
        }
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void fewerOrdersThanRidersAreAllAssigned() throws Exception {
        Dispatcher dispatcher = dispatcherWithRiders(50);

        List<AssignmentResult> results = assignConcurrently(dispatcher, 30);

        assertThat(results).allMatch(Assigned.class::isInstance);
        assertThat(results.stream().map(r -> ((Assigned) r).rider().id()).distinct()).hasSize(30);
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void noRiderEverHoldsTwoOrdersWhileOrdersAreAssignedAndCompleted() throws Exception {
        Dispatcher dispatcher = dispatcherWithRiders(5);
        Map<String, AtomicInteger> ordersHeld = new ConcurrentHashMap<>();
        AtomicInteger maxHeld = new AtomicInteger();
        AtomicInteger delivered = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                int thread = t;
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < 2_000; i++) {
                        Order order = new Order("t" + thread + "-o" + i, RESTAURANT);
                        if (dispatcher.assign(order) instanceof Assigned(Order o, Rider rider)) {
                            AtomicInteger held = ordersHeld.computeIfAbsent(rider.id(), id -> new AtomicInteger());
                            maxHeld.accumulateAndGet(held.incrementAndGet(), Math::max);
                            held.decrementAndGet(); // before complete: the rider is still ours
                            assertThat(dispatcher.complete(o.id())).isTrue();
                            delivered.incrementAndGet();
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(maxHeld.get()).isEqualTo(1);
        assertThat(delivered.get()).isPositive();
        for (int i = 0; i < 5; i++) {
            assertThat(dispatcher.isBusy("r" + i)).isFalse();
        }
    }
}
