package com.amigoscode.orderapi.order;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class OrderService {

    private final InMemoryOrderRepository repository;
    private final Clock clock;

    /**
     * Idempotency key (scoped to the customer) to the request that first used it and the order it
     * created. In production this lives in Redis or a DB table with a TTL (24h is common).
     */
    private final ConcurrentMap<String, IdempotencyRecord> idempotencyKeys = new ConcurrentHashMap<>();

    private record IdempotencyRecord(CreateOrderRequest request, UUID orderId) {
    }

    public OrderService(InMemoryOrderRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    // Story 1

    public Order place(CreateOrderRequest request) {
        List<OrderItem> items = request.items().stream().map(OrderItemRequest::toItem).toList();
        return repository.save(Order.create(request.customerId(), request.restaurantId(), items, clock.instant()));
    }

    public Order get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    // Story 2

    public OrderPage listForCustomer(String customerId, int page, int size) {
        List<Order> all = repository.findByCustomerId(customerId);
        int from = (int) Math.min((long) page * size, all.size());
        int to = Math.min(from + size, all.size());
        int totalPages = (all.size() + size - 1) / size;
        return new OrderPage(all.subList(from, to), page, size, all.size(), totalPages);
    }

    public Order updateItems(UUID id, UpdateOrderItemsRequest request) {
        List<OrderItem> items = request.items().stream().map(OrderItemRequest::toItem).toList();
        return repository.update(id, order -> order.withItems(items))
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    public Order cancel(UUID id) {
        return repository.update(id, Order::cancel).orElseThrow(() -> new OrderNotFoundException(id));
    }

    // Story 4

    /**
     * Places an order at most once per key. {@code computeIfAbsent} runs the creation atomically
     * for a given key, so two simultaneous requests with the same key create exactly one order;
     * the loser waits and then sees the winner's record.
     */
    public Placement place(CreateOrderRequest request, String idempotencyKey) {
        String scopedKey = request.customerId() + ":" + idempotencyKey;
        UUID[] created = new UUID[1];
        IdempotencyRecord record = idempotencyKeys.computeIfAbsent(scopedKey, key -> {
            created[0] = place(request).id();
            return new IdempotencyRecord(request, created[0]);
        });
        if (!record.request().equals(request)) {
            throw new IdempotencyKeyReusedException(idempotencyKey);
        }
        boolean replayed = !record.orderId().equals(created[0]);
        return new Placement(get(record.orderId()), replayed);
    }
}
