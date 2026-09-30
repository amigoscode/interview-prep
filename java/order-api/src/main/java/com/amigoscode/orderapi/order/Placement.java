package com.amigoscode.orderapi.order;

/**
 * The result of placing an order.
 *
 * @param order    the order, new or original
 * @param replayed {@code true} when an earlier request with the same idempotency key created it
 */
public record Placement(Order order, boolean replayed) {
}
