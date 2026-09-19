package com.amigoscode.shortener;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Story 4. */
class UrlShortenerConcurrencyTest {

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void twentyThreadsShorteningTheSameUrlCreateExactlyOneCode() throws Exception {
        AtomicInteger generated = new AtomicInteger();
        CodeGenerator counting = () -> "c" + generated.incrementAndGet();
        UrlShortener s = new UrlShortener(counting);

        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return s.shorten("https://amigoscode.com/java");
            }));
        }
        start.countDown();
        Set<String> codes = new HashSet<>();
        for (Future<String> r : results) codes.add(r.get());
        pool.shutdown();

        assertThat(codes).hasSize(1);
        assertThat(generated.get()).isEqualTo(1);
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void manyThreadsManyUrlsStayConsistent() throws Exception {
        UrlShortener s = new UrlShortener();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<?>> tasks = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            tasks.add(pool.submit(() -> {
                for (int i = 0; i < 2_000; i++) {
                    String url = "https://example.com/" + (i % 500);
                    String code = s.shorten(url);
                    assertThat(s.expand(code)).contains(url);
                }
                return null;
            }));
        }
        for (Future<?> task : tasks) task.get();
        pool.shutdown();
    }
}
