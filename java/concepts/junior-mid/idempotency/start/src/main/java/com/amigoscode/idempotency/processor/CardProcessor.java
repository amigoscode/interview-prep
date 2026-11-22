package com.amigoscode.idempotency.processor;

/**
 * Stands in for the bank or card network. It is the part of a payment that cannot be undone by a
 * database rollback, and it takes time: that time is the window in which a second copy of the same
 * request can arrive.
 */
public interface CardProcessor {

    void charge(long customerId, long amountCents);
}
