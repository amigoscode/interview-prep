package com.amigoscode.orders;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consumes a stream of order events: at-least-once, possibly out of order, from many threads.
 *
 * <ul>
 *   <li>Duplicates (same eventId) are ignored.</li>
 *   <li>A sequence not newer than the current one is ignored as stale.</li>
 *   <li>A gap is accepted if the new state is reachable (accept-the-latest). The producer
 *       assigns sequences, so the newest event is the truth; the missing one will arrive later
 *       and be dropped as stale. No buffer means nothing to bound or time out.</li>
 * </ul>
 *
 * Thread safety: one lock per order (the {@link Order} monitor), created once with
 * {@code computeIfAbsent}. The two aggregates guard themselves.
 */
public final class OrderTracker {

    static final Duration TOP_RESTAURANTS_WINDOW = Duration.ofHours(1);
    static final int DEFAULT_AVERAGE_OVER = 100;

    private final Clock clock;
    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final RecentOrders recentOrders = new RecentOrders(TOP_RESTAURANTS_WINDOW);
    private final DeliveryTimes deliveryTimes;

    public OrderTracker(Clock clock) {
        this(clock, DEFAULT_AVERAGE_OVER);
    }

    public OrderTracker(Clock clock, int averageOverLastN) {
        if (averageOverLastN <= 0) throw new IllegalArgumentException("averageOverLastN must be positive");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.deliveryTimes = new DeliveryTimes(averageOverLastN);
    }

    // ---- Story 1 and 2 ----

    public ApplyResult apply(OrderEvent event) {
        Objects.requireNonNull(event, "event");
        boolean[] created = {false};
        Order order = orders.computeIfAbsent(event.orderId(), id -> {
            created[0] = true;
            return new Order(event.order());
        });
        if (created[0]) {
            recentOrders.record(event.order().restaurantId(), event.order().placedAt(), clock.instant());
        }
        ApplyResult result = order.apply(event);
        if (result instanceof ApplyResult.Applied(OrderState state) && state == OrderState.DELIVERED) {
            // DELIVERED is terminal, so this runs at most once per order.
            deliveryTimes.record(Duration.between(order.details().placedAt(), event.occurredAt()));
        }
        return result;
    }

    public Optional<OrderState> stateOf(String orderId) {
        Order order = orders.get(orderId);
        return order == null ? Optional.empty() : order.state();
    }

    // ---- Story 3 ----

    /** Open orders past their promised time, per zone. Zones with none are left out. */
    public Map<String, Long> lateOrdersPerZone() {
        Instant now = clock.instant();
        Map<String, Long> perZone = new TreeMap<>();
        for (Order order : orders.values()) {
            if (order.isLateAt(now)) perZone.merge(order.details().zoneId(), 1L, Long::sum);
        }
        return perZone;
    }

    /** Orders delivered after their promised time, with the delivery in (now - window, now]. */
    public long lateDeliveriesInLast(Duration window) {
        Objects.requireNonNull(window, "window");
        if (window.isNegative() || window.isZero()) throw new IllegalArgumentException("window must be positive");
        Instant now = clock.instant();
        Instant from = now.minus(window);
        return orders.values().stream()
                .flatMap(order -> order.lateDeliveryAt().stream())
                .filter(at -> at.isAfter(from) && !at.isAfter(now))
                .count();
    }

    // ---- Story 4 ----

    /** The k restaurants with the most orders placed in the last hour, busiest first. */
    public List<RestaurantCount> topRestaurants(int k) {
        if (k <= 0) throw new IllegalArgumentException("k must be positive");
        return recentOrders.top(k, clock.instant());
    }

    /** Average of placedAt-to-delivered over the last N delivered orders. */
    public Optional<Duration> averageDeliveryTime() {
        return deliveryTimes.average();
    }
}
