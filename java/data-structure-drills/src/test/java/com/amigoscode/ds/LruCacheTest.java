package com.amigoscode.ds;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LruCacheTest {

    @Test
    void evictsTheLeastRecentlyUsedKey() {
        // LeetCode 146 example
        LruCache<Integer, Integer> cache = new LruCache<>(2);
        cache.put(1, 1);
        cache.put(2, 2);
        assertThat(cache.get(1)).contains(1);
        cache.put(3, 3);                        // evicts 2
        assertThat(cache.get(2)).isEmpty();
        cache.put(4, 4);                        // evicts 1
        assertThat(cache.get(1)).isEmpty();
        assertThat(cache.get(3)).contains(3);
        assertThat(cache.get(4)).contains(4);
    }

    @Nested
    class Basics {

        @Test
        void missingKeyIsEmpty() {
            assertThat(new LruCache<String, String>(1).get("nope")).isEmpty();
        }

        @Test
        void rejectsNonPositiveCapacityAndNulls() {
            assertThatThrownBy(() -> new LruCache<>(0)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new LruCache<>(-3)).isInstanceOf(IllegalArgumentException.class);
            LruCache<String, String> cache = new LruCache<>(1);
            assertThatThrownBy(() -> cache.put(null, "v")).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> cache.put("k", null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> cache.get(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void neverHoldsMoreThanCapacity() {
            LruCache<Integer, Integer> cache = new LruCache<>(3);
            for (int i = 0; i < 100; i++) {
                cache.put(i, i);
            }
            assertThat(cache.size()).isEqualTo(3);
            assertThat(cache.keysByRecency()).containsExactly(99, 98, 97);
        }

        @Test
        void capacityOneKeepsOnlyTheLatest() {
            LruCache<String, Integer> cache = new LruCache<>(1);
            cache.put("a", 1);
            cache.put("b", 2);
            assertThat(cache.get("a")).isEmpty();
            assertThat(cache.get("b")).contains(2);
        }
    }

    @Nested
    class Recency {

        @Test
        void putOnAnExistingKeyUpdatesTheValueWithoutEvicting() {
            LruCache<String, Integer> cache = new LruCache<>(2);
            cache.put("a", 1);
            cache.put("b", 2);
            cache.put("a", 10);
            assertThat(cache.size()).isEqualTo(2);
            assertThat(cache.get("a")).contains(10);
            assertThat(cache.get("b")).contains(2);
        }

        @Test
        void putOnAnExistingKeyMakesItMostRecent() {
            LruCache<String, Integer> cache = new LruCache<>(2);
            cache.put("a", 1);
            cache.put("b", 2);
            cache.put("a", 10);                 // a is now most recent
            cache.put("c", 3);                  // so b goes
            assertThat(cache.get("b")).isEmpty();
            assertThat(cache.get("a")).contains(10);
        }

        @Test
        void getMakesAKeyMostRecent() {
            LruCache<String, Integer> cache = new LruCache<>(3);
            cache.put("a", 1);
            cache.put("b", 2);
            cache.put("c", 3);
            cache.get("a");
            assertThat(cache.keysByRecency()).containsExactly("a", "c", "b");
        }

        @Test
        void aMissDoesNotChangeTheOrder() {
            LruCache<String, Integer> cache = new LruCache<>(2);
            cache.put("a", 1);
            cache.put("b", 2);
            cache.get("zzz");
            assertThat(cache.keysByRecency()).containsExactly("b", "a");
        }
    }

    @Nested
    class Stretch_ThreadSafety {

        @Test
        void manyThreadsNeverCorruptTheListOrExceedCapacity() throws Exception {
            int capacity = 50;
            LruCache<Integer, Integer> cache = new LruCache<>(capacity);
            int threads = 8;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch startGate = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            try {
                for (int t = 0; t < threads; t++) {
                    futures.add(pool.submit(() -> {
                        startGate.await();
                        ThreadLocalRandom random = ThreadLocalRandom.current();
                        for (int i = 0; i < 20_000; i++) {
                            int key = random.nextInt(200);
                            if (random.nextBoolean()) {
                                cache.put(key, key);
                            } else {
                                cache.get(key).ifPresent(v -> assertThat(v).isEqualTo(key));
                            }
                        }
                        return null;
                    }));
                }
                startGate.countDown();
                for (Future<?> f : futures) {
                    f.get(30, TimeUnit.SECONDS); // rethrows any assertion failure from a worker
                }
            } finally {
                pool.shutdownNow();
            }

            List<Integer> keys = cache.keysByRecency();
            assertThat(cache.size()).isEqualTo(capacity);
            assertThat(keys).hasSize(capacity).doesNotHaveDuplicates();
            keys.forEach(k -> assertThat(cache.get(k)).contains(k));
        }
    }
}
