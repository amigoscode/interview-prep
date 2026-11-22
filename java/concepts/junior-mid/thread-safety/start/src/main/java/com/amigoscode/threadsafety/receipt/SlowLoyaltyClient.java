package com.amigoscode.threadsafety.receipt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Stands in for the loyalty service: takes a few milliseconds, then answers 1 point per 100 cents. */
@Component
public class SlowLoyaltyClient implements LoyaltyClient {

    private final long lookupMillis;

    public SlowLoyaltyClient(@Value("${shop.loyalty-lookup-millis:20}") long lookupMillis) {
        this.lookupMillis = lookupMillis;
    }

    @Override
    public int pointsFor(String customer, int amountCents) {
        try {
            Thread.sleep(lookupMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        return amountCents / 100;
    }
}
