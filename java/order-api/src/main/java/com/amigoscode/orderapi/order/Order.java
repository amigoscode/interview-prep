package com.amigoscode.orderapi.order;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * An order. Immutable: every change produces a new instance, which is what lets the repository
 * swap it atomically.
 */
public record Order(
        UUID id,
        String customerId,
        String restaurantId,
        List<OrderItem> items,
        OrderStatus status,
        long totalCents,
        Instant createdAt) {

    public Order {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        items = List.copyOf(items);
    }

    static Order create(String customerId, String restaurantId, List<OrderItem> items, Instant now) {
        return new Order(UUID.randomUUID(), customerId, restaurantId, items, OrderStatus.CREATED, totalOf(items), now);
    }

    Order withItems(List<OrderItem> newItems) {
        requireModifiable();
        return new Order(id, customerId, restaurantId, newItems, status, totalOf(newItems), createdAt);
    }

    /** Cancelling twice is a no-op, so a retried cancel gets the same answer as the first. */
    Order cancel() {
        return status == OrderStatus.CANCELLED
                ? this
                : new Order(id, customerId, restaurantId, items, OrderStatus.CANCELLED, totalCents, createdAt);
    }

    private void requireModifiable() {
        if (status != OrderStatus.CREATED) {
            throw new OrderNotModifiableException(id, status);
        }
    }

    private static long totalOf(List<OrderItem> items) {
        return items.stream().mapToLong(OrderItem::lineTotalCents).reduce(0L, Math::addExact);
    }
}
