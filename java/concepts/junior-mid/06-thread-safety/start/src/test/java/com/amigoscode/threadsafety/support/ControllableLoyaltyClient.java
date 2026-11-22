package com.amigoscode.threadsafety.support;

import com.amigoscode.threadsafety.receipt.LoyaltyClient;

/**
 * Replaces the slow loyalty service in tests. It can hold every caller at a {@link Meeting}, so
 * that all the customers are inside ReceiptService.receiptFor at the same moment.
 */
public class ControllableLoyaltyClient implements LoyaltyClient {

    private volatile Meeting meeting;

    public void reset() {
        meeting = null;
    }

    public void everyoneIsLookedUpAtTheSameMoment(Meeting meeting) {
        this.meeting = meeting;
    }

    @Override
    public int pointsFor(String customer, int amountCents) {
        Meeting current = meeting;
        if (current != null) {
            current.arrive();
        }
        return amountCents / 100;
    }
}
