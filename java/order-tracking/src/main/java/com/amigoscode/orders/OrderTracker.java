package com.amigoscode.orders;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Story 1 starts here. The Clock is already in the constructor because story 3 needs it. */
public final class OrderTracker {

    public OrderTracker(Clock clock) {
        throw new UnsupportedOperationException("story 1");
    }

    /** Story 4: the moving average of delivery time covers the last {@code averageOverLastN} deliveries. */
    public OrderTracker(Clock clock, int averageOverLastN) {
        throw new UnsupportedOperationException("story 4");
    }

    public ApplyResult apply(OrderEvent event) {
        throw new UnsupportedOperationException("story 1");
    }

    public Optional<OrderState> stateOf(String orderId) {
        throw new UnsupportedOperationException("story 1");
    }

    public Map<String, Long> lateOrdersPerZone() {
        throw new UnsupportedOperationException("story 3");
    }

    public long lateDeliveriesInLast(Duration window) {
        throw new UnsupportedOperationException("story 3");
    }

    public List<RestaurantCount> topRestaurants(int k) {
        throw new UnsupportedOperationException("story 4");
    }

    public Optional<Duration> averageDeliveryTime() {
        throw new UnsupportedOperationException("story 4");
    }
}
