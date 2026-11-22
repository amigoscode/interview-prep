package com.amigoscode.threadsafety.pricing;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stands in for a call to the supplier's pricing API: slow, and every call costs money. It counts
 * its calls so the demo can show how many were made.
 */
@Component
public class SupplierPriceSource implements PriceSource {

    private final long lookupMillis;
    private final AtomicInteger calls = new AtomicInteger();

    public SupplierPriceSource(@Value("${shop.price-lookup-millis:50}") long lookupMillis) {
        this.lookupMillis = lookupMillis;
    }

    @Override
    public BigDecimal fetchPrice(String product) {
        calls.incrementAndGet();
        try {
            Thread.sleep(lookupMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        // Any stable number will do: the same product always costs the same.
        return BigDecimal.valueOf(100 + Math.floorMod(product.hashCode(), 900)).setScale(2);
    }

    public int calls() {
        return calls.get();
    }

    public void reset() {
        calls.set(0);
    }
}
