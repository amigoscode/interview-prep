package com.amigoscode.locking.order;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * Picks the strategy. Deliberately NOT transactional: the optimistic retry has to start a brand new
 * transaction for every attempt, so the loop lives outside the transactional bean.
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
            case OPTIMISTIC -> placeOptimisticWithRetry(productId, quantity);
            case ATOMIC -> placer.placeAtomic(productId, quantity);
        };
    }

    private Order placeOptimisticWithRetry(long productId, int quantity) {
        for (int attempt = 1; ; attempt++) {
            try {
                return placer.placeOptimistic(productId, quantity);
            } catch (OptimisticLockingFailureException e) {
                // Someone else changed the row between our read and our write. The transaction is
                // already rolled back, so trying again re-reads the fresh stock.
                if (attempt == MAX_ATTEMPTS) {
                    throw new OrderConflictException(productId, attempt);
                }
            }
        }
    }
}
