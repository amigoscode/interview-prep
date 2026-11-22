package com.amigoscode.threadsafety.support;

import com.amigoscode.threadsafety.pricing.PriceSource;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Replaces the slow supplier in tests. It counts the calls per product, and it can hold every
 * caller at a {@link Meeting}: the fetch sits between PriceCache's CHECK and its ACT, so that is
 * the place to make the threads overlap.
 */
public class ControllablePriceSource implements PriceSource {

    private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();
    private volatile Meeting meeting;

    public void reset() {
        calls.clear();
        meeting = null;
    }

    public void everyoneFetchesAtTheSameMoment(Meeting meeting) {
        this.meeting = meeting;
    }

    public int calls(String product) {
        AtomicInteger count = calls.get(product);
        return count == null ? 0 : count.get();
    }

    public int totalCalls() {
        return calls.values().stream().mapToInt(AtomicInteger::get).sum();
    }

    @Override
    public BigDecimal fetchPrice(String product) {
        calls.computeIfAbsent(product, key -> new AtomicInteger()).incrementAndGet();
        Meeting current = meeting;
        if (current != null) {
            current.arrive();
        }
        return new BigDecimal("499.00");
    }
}
