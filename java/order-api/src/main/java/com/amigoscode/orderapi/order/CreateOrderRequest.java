package com.amigoscode.orderapi.order;

import java.util.List;

/** Body of {@code POST /orders}. There is no total on purpose: the server computes it. */
public record CreateOrderRequest(String customerId, String restaurantId, List<OrderItemRequest> items) {
}
