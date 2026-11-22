package com.amigoscode.threadsafety.sales;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

/** Today's sales: how many orders, and how much money. The dashboard reads both together. */
@Service
public class SalesStats {

    // Each field is thread-safe on its own. Nothing keeps the two of them in step.
    private final AtomicLong orders = new AtomicLong();
    private final AtomicLong revenueCents = new AtomicLong();

    // TODO Task 8: the two updates must happen as one, and a reader must never see one without the other.
    public void record(long amountCents) {
        orders.incrementAndGet();
        revenueCents.addAndGet(amountCents);
    }

    public SalesSummary summary() {
        return new SalesSummary(orders.get(), revenueCents.get());
    }

    public void reset() {
        orders.set(0);
        revenueCents.set(0);
    }
}
