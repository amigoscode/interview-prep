package com.amigoscode.orderapi.order;

/** One line of an order, as stored and returned. */
public record OrderItem(String name, int quantity, long priceCents) {

    long lineTotalCents() {
        return Math.multiplyExact(quantity, priceCents);
    }
}
