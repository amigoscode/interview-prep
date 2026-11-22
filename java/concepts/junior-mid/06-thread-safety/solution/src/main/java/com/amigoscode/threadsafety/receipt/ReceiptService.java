package com.amigoscode.threadsafety.receipt;

import org.springframework.stereotype.Service;

/** Builds the receipt a customer sees after paying. */
@Service
public class ReceiptService {

    // The only field is a final reference to another bean. Nothing here belongs to a request.
    private final LoyaltyClient loyalty;

    public ReceiptService(LoyaltyClient loyalty) {
        this.loyalty = loyalty;
    }

    public Receipt receiptFor(String customer, int amountCents) {
        // Parameters and local variables live on the stack of the thread that runs this call.
        // No other thread can see them.
        int points = loyalty.pointsFor(customer, amountCents);
        return buildReceipt(customer, amountCents, points);
    }

    private Receipt buildReceipt(String customer, int amountCents, int points) {
        return new Receipt(customer, amountCents, points);
    }
}
