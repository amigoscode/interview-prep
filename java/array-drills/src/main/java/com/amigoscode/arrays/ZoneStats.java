package com.amigoscode.arrays;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

/**
 * Collections follow-up: group today's orders by zone.
 *
 * <p>Ties for the busiest zone go to the zone name that sorts first, so the answer does not depend
 * on {@link java.util.HashMap} iteration order. No orders means no busiest zone:
 * {@link Optional#empty()}, not an exception, because a quiet day is not an error.
 */
public final class ZoneStats {

    private ZoneStats() {
    }

    /** Zone to order count, iterated in zone-name order. Zones with no orders are absent. O(n log z). */
    public static Map<String, Long> ordersPerZone(List<Order> orders) {
        Objects.requireNonNull(orders, "orders");
        return orders.stream().collect(groupingBy(Order::zone, TreeMap::new, counting()));
    }

    /** The zone with the most orders; on a tie, the alphabetically first. O(n) time, O(z) space. */
    public static Optional<String> busiestZone(List<Order> orders) {
        Objects.requireNonNull(orders, "orders");
        // max() keeps the greatest element, so among equal counts the "greatest" key must be the
        // alphabetically first one: hence the reversed key comparator.
        Comparator<Map.Entry<String, Long>> byCountThenFirstName =
                Map.Entry.<String, Long>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder()));
        return orders.stream()
                .collect(groupingBy(Order::zone, counting()))
                .entrySet().stream()
                .max(byCountThenFirstName)
                .map(Map.Entry::getKey);
    }
}
