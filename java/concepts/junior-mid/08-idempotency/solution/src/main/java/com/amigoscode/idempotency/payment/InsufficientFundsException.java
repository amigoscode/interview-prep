package com.amigoscode.idempotency.payment;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(long customerId, long amountCents) {
        super("Customer " + customerId + " does not have " + amountCents + " cents");
    }
}
