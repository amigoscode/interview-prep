package com.amigoscode.threadsafety.pricing;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * EXHIBIT for Task 9. Do not fix: run ./demo --scenario locked-cache to see what it costs.
 *
 * This cache is correct. Every thread takes the same lock, so the HashMap is never touched by two
 * threads at once and no price is fetched twice. But the lock is held during the slow supplier
 * call, so a request for one product makes the requests for every other product wait.
 */
@Service
public class LockedPriceCache {

    private final Map<String, BigDecimal> prices = new HashMap<>();
    private final PriceSource source;

    public LockedPriceCache(PriceSource source) {
        this.source = source;
    }

    public synchronized BigDecimal priceOf(String product) {
        if (!prices.containsKey(product)) {
            prices.put(product, source.fetchPrice(product));      // slow, and the lock is held the whole time
        }
        return prices.get(product);
    }
}
