package com.amigoscode.pagination.order;

import java.time.Instant;

/** One order, as the API returns it. */
public record OrderResponse(long id, long customerId, String status, int totalCents, Instant createdAt) {

    static OrderResponse of(Order order) {
        return new OrderResponse(order.getId(), order.getCustomerId(), order.getStatus(), order.getTotalCents(),
                order.getCreatedAt());
    }
}
