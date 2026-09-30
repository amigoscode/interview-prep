package com.amigoscode.orderapi.order;

import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.UnaryOperator;

@Repository
public class InMemoryOrderRepository {

    /** Newest first; the id breaks ties so paging is stable when two orders share an instant. */
    private static final Comparator<Order> NEWEST_FIRST =
            Comparator.comparing(Order::createdAt).reversed().thenComparing(Order::id);

    private final ConcurrentMap<UUID, Order> orders = new ConcurrentHashMap<>();

    public Order save(Order order) {
        orders.put(order.id(), order);
        return order;
    }

    public Optional<Order> findById(UUID id) {
        return Optional.ofNullable(orders.get(id));
    }

    /**
     * Applies {@code change} atomically: two concurrent updates to the same order cannot both read
     * the old version. Empty when there is no such order.
     */
    public Optional<Order> update(UUID id, UnaryOperator<Order> change) {
        return Optional.ofNullable(orders.computeIfPresent(id, (key, current) -> change.apply(current)));
    }

    public List<Order> findByCustomerId(String customerId) {
        return orders.values().stream()
                .filter(order -> order.customerId().equals(customerId))
                .sorted(NEWEST_FIRST)
                .toList();
    }
}
