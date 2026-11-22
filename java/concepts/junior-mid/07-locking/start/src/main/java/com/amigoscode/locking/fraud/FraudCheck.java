package com.amigoscode.locking.fraud;

/**
 * Stands in for the work a real shop does between reading the stock and writing it: pricing, fraud
 * rules, a voucher lookup. It takes time, and that time is the window in which a second buyer can
 * read the same stock value.
 */
public interface FraudCheck {

    void review(long productId, int quantity);
}
