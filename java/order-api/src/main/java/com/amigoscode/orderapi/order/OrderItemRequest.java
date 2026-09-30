package com.amigoscode.orderapi.order;

/**
 * One line of an incoming order. Boxed types so a missing field arrives as {@code null} rather
 * than silently becoming {@code 0}. What you do about that is up to you (story 3).
 */
public record OrderItemRequest(String name, Integer quantity, Long priceCents) {
}
