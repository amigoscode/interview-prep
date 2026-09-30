package com.amigoscode.orderapi.order;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** An order, and the JSON the API returns for one. */
public record Order(
        UUID id,
        String customerId,
        String restaurantId,
        List<OrderItem> items,
        OrderStatus status,
        long totalCents,
        Instant createdAt) {
}
