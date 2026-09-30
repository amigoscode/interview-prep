package com.amigoscode.orderapi.order;

import java.util.UUID;

public class OrderNotModifiableException extends RuntimeException {

    public OrderNotModifiableException(UUID id, OrderStatus status) {
        super("Order " + id + " is " + status + " and can no longer be changed");
    }
}
