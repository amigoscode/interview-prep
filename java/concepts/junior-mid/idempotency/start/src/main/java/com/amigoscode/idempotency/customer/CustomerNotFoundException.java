package com.amigoscode.idempotency.customer;

public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(long customerId) {
        super("Customer " + customerId + " does not exist");
    }
}
