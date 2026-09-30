package com.amigoscode.orders;

import java.time.Instant;

/**
 * One message from the order stream. {@code sequence} is assigned per order by the producer
 * (1, 2, 3, ...); {@code eventId} is unique per message and repeats only on redelivery (story 2).
 */
public record OrderEvent(String eventId, String orderId, long sequence, OrderState state,
                         Instant occurredAt, OrderDetails order) {}
