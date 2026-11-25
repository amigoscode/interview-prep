package com.amigoscode.threadsafety.receipt;

/** Asks the loyalty service how many points a purchase earns. A network call in real life. */
public interface LoyaltyClient {

    int pointsFor(String customer, int amountCents);
}
