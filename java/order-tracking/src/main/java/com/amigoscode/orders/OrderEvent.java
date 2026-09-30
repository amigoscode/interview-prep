package com.amigoscode.orders;

import java.time.Instant;
import java.util.Objects;

/**
 * One message from the order stream. {@code sequence} is assigned per order by the producer
 * (1, 2, 3, ...); {@code eventId} is unique per message and repeats only on redelivery.
 */
public record OrderEvent(String eventId, String orderId, long sequence, OrderState state,
                         Instant occurredAt, OrderDetails order) {

    public OrderEvent {
        OrderDetails.requireText(eventId, "eventId");
        OrderDetails.requireText(orderId, "orderId");
        if (sequence < 1) throw new IllegalArgumentException("sequence must be positive");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(order, "order");
    }
}
