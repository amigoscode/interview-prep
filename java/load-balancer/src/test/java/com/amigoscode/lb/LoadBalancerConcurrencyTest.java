package com.amigoscode.lb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Story 5. */
class LoadBalancerConcurrencyTest {

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void hammeringRegisterUnregisterAndGetNeverThrowsOrCorrupts() throws Exception {
        LoadBalancer lb = new LoadBalancer(8, new RoundRobinSelection());
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<?>> tasks = new ArrayList<>();
        for (int t = 0; t < 10; t++) {
            tasks.add(pool.submit(() -> {
                for (int i = 0; i < 5_000; i++) {
                    String address = "10.0.0." + ThreadLocalRandom.current().nextInt(12);
                    lb.register(address);
                    try {
                        assertThat(lb.get()).startsWith("10.0.0.");
                    } catch (NoInstancesException raceWithAnotherThreadsUnregister) {
                        // acceptable: the set was momentarily empty
                    }
                    lb.unregister(address);
                }
                return null;
            }));
        }
        for (Future<?> task : tasks) task.get(); // rethrows ConcurrentModificationException etc.
        pool.shutdown();
        assertThat(lb.size()).isBetween(0, 8);
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void roundRobinStaysFairUnderConcurrentGets() throws Exception {
        LoadBalancer lb = new LoadBalancer(3, new RoundRobinSelection());
        lb.register("a"); lb.register("b"); lb.register("c");

        ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<?>> tasks = new ArrayList<>();
        for (int t = 0; t < 6; t++) {
            tasks.add(pool.submit(() -> {
                for (int i = 0; i < 1_000; i++) {
                    counts.computeIfAbsent(lb.get(), k -> new AtomicInteger()).incrementAndGet();
                }
                return null;
            }));
        }
        for (Future<?> task : tasks) task.get();
        pool.shutdown();

        assertThat(counts.get("a").get()).isEqualTo(2_000);
        assertThat(counts.get("b").get()).isEqualTo(2_000);
        assertThat(counts.get("c").get()).isEqualTo(2_000);
    }
}
