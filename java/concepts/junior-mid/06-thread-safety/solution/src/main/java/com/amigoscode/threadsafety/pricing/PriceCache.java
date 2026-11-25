package com.amigoscode.threadsafety.pricing;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** Remembers prices so the slow supplier is asked only once per product. */
@Service
public class PriceCache {

    private final Map<String, BigDecimal> prices = new ConcurrentHashMap<>();
    private final PriceSource source;

    public PriceCache(PriceSource source) {
        this.source = source;
    }

    public BigDecimal priceOf(String product) {
        // Check and act in ONE call. A second thread asking for the same product waits for the
        // first one's fetch and then gets its result.
        return prices.computeIfAbsent(product, source::fetchPrice);
    }

    public void reset() {
        prices.clear();
    }
}
