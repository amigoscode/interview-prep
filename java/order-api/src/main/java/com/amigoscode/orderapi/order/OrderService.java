package com.amigoscode.orderapi.order;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

/** Everything behind the controller. Add a repository (a ConcurrentHashMap is fine) as you need it. */
@Service
public class OrderService {

    private final Clock clock;

    public OrderService(Clock clock) {
        this.clock = clock;
    }

    public Order place(CreateOrderRequest request) {
        throw new UnsupportedOperationException("story 1");
    }

    public Order get(UUID id) {
        throw new UnsupportedOperationException("story 1");
    }

    public OrderPage listForCustomer(String customerId, int page, int size) {
        throw new UnsupportedOperationException("story 2");
    }

    public Order updateItems(UUID id, UpdateOrderItemsRequest request) {
        throw new UnsupportedOperationException("story 2");
    }

    public Order cancel(UUID id) {
        throw new UnsupportedOperationException("story 2");
    }

    public Placement place(CreateOrderRequest request, String idempotencyKey) {
        throw new UnsupportedOperationException("story 4");
    }
}
