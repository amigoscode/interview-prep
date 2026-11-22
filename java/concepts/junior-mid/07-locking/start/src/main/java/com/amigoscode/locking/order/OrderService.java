package com.amigoscode.locking.order;

import org.springframework.stereotype.Service;

/**
 * Picks the strategy. Deliberately NOT transactional: each OrderPlacer method is its own
 * transaction, and this class decides what to do when one of them fails.
 */
@Service
public class OrderService {

    public static final int MAX_ATTEMPTS = 3;

    private final OrderPlacer placer;

    public OrderService(OrderPlacer placer) {
        this.placer = placer;
    }

    public Order placeOrder(Strategy strategy, long productId, int quantity) {
        return switch (strategy) {
            case NAIVE -> placer.placeNaive(productId, quantity);
            case PESSIMISTIC -> placer.placePessimistic(productId, quantity);
            // TODO Task 4: when the version check fails, try again, up to MAX_ATTEMPTS attempts in
            //      total. If every attempt fails, throw OrderConflictException.
            case OPTIMISTIC -> placer.placeOptimistic(productId, quantity);
            case ATOMIC -> placer.placeAtomic(productId, quantity);
        };
    }
}
