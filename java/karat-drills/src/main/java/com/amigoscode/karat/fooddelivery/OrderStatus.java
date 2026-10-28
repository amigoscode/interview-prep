package com.amigoscode.karat.fooddelivery;

/**
 * An order moves PLACED, PREPARING, OUT_FOR_DELIVERY, DELIVERED, or is CANCELED. Orders are
 * active until delivered or canceled.
 */
public enum OrderStatus {
    PLACED,
    PREPARING,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELED
}
