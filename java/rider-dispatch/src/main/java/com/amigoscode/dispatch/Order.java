package com.amigoscode.dispatch;

import java.util.Objects;

/** An order waiting for a rider, picked up at the restaurant. */
public record Order(String id, Location restaurant) {

    public Order {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("order id must not be blank");
        }
        Objects.requireNonNull(restaurant, "restaurant");
    }
}
