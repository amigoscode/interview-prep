package com.amigoscode.fetching.order;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(long id) {
        super("Order " + id + " does not exist");
    }
}
