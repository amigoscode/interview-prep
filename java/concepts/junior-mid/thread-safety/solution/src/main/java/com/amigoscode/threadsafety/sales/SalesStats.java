package com.amigoscode.threadsafety.sales;

import org.springframework.stereotype.Service;

/** Today's sales: how many orders, and how much money. The dashboard reads both together. */
@Service
public class SalesStats {

    // Plain fields, guarded by the lock of this object. Every method that touches them is synchronized.
    private long orders;
    private long revenueCents;

    /** Both updates happen while holding the lock, so nobody can see one without the other. */
    public synchronized void record(long amountCents) {
        orders++;
        revenueCents += amountCents;
    }

    /** The reader takes the SAME lock. Without it, it could still read between the two updates. */
    public synchronized SalesSummary summary() {
        return new SalesSummary(orders, revenueCents);
    }

    public synchronized void reset() {
        orders = 0;
        revenueCents = 0;
    }
}
