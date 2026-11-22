package com.amigoscode.threadsafety.receipt;

import org.springframework.stereotype.Service;

/** Builds the receipt a customer sees after paying. */
@Service
public class ReceiptService {

    private final LoyaltyClient loyalty;

    // TODO Task 6: these three fields hold the data of ONE request, in a bean shared by ALL requests.
    private String customer;
    private int amountCents;
    private int points;

    public ReceiptService(LoyaltyClient loyalty) {
        this.loyalty = loyalty;
    }

    public Receipt receiptFor(String customer, int amountCents) {
        this.customer = customer;
        this.amountCents = amountCents;
        this.points = loyalty.pointsFor(customer, amountCents);   // slow: other requests arrive meanwhile
        return buildReceipt();
    }

    private Receipt buildReceipt() {
        return new Receipt(customer, amountCents, points);        // whose customer is in the field by now?
    }
}
