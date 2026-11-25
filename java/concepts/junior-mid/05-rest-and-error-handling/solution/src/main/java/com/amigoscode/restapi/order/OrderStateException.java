package com.amigoscode.restapi.order;

/** The request is fine, but the order is in a state that does not allow it. */
public class OrderStateException extends RuntimeException {

    public OrderStateException(String message) {
        super(message);
    }
}
