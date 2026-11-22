package com.amigoscode.threadsafety.pricing;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** Remembers prices so the slow supplier is asked only once per product. */
@Service
public class PriceCache {

    // Already a ConcurrentHashMap. That does not make the method below thread-safe.
    private final Map<String, BigDecimal> prices = new ConcurrentHashMap<>();
    private final PriceSource source;

    public PriceCache(PriceSource source) {
        this.source = source;
    }

    public BigDecimal priceOf(String product) {
        // TODO Task 4, step 3: check-then-act. Two threads can both see "not there" and both fetch.
        if (!prices.containsKey(product)) {                       // CHECK
            prices.put(product, source.fetchPrice(product));      // ACT (slow)
        }
        return prices.get(product);
    }

    public void reset() {
        prices.clear();
    }
}
