package com.amigoscode.threadsafety.sales;

import org.springframework.stereotype.Service;

/**
 * EXHIBIT for Task 9. Do not fix: it stays broken so you can always run ./demo --scenario wrong-lock.
 *
 * It uses synchronized, and it is still not thread-safe. A lock only works when every thread locks
 * the SAME object. Here every call to record() makes its own lock object, so nobody ever waits.
 */
@Service
public class WrongLockSalesStats {

    private long orders;
    private long revenueCents;

    public void record(long amountCents) {
        Object lock = new Object();               // a brand new lock for every call
        synchronized (lock) {                     // nobody else can ever hold this one
            orders++;
            revenueCents += amountCents;
        }
    }

    public SalesSummary summary() {
        synchronized (this) {                     // a different object from the one record() locks
            return new SalesSummary(orders, revenueCents);
        }
    }

    public void reset() {
        synchronized (this) {
            orders = 0;
            revenueCents = 0;
        }
    }
}
