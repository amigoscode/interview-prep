package com.amigoscode.fetching.order;

import java.time.Instant;

/** One row of the order list. This is what GET /api/orders returns, whatever the fetch plan. */
public record OrderSummary(long id, OrderStatus status, Instant placedAt, String customerName, String customerCity) {

    /** Reads the customer. If the customer is a lazy proxy, this is the line that makes Hibernate load it. */
    public static OrderSummary from(Order order) {
        return new OrderSummary(
                order.getId(),
                order.getStatus(),
                order.getPlacedAt(),
                order.getCustomer().getName(),
                order.getCustomer().getCity());
    }
}
