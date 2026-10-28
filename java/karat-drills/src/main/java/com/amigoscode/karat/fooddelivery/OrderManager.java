package com.amigoscode.karat.fooddelivery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * We are building a program to manage a food delivery platform. The platform has multiple
 * restaurants, customers place orders, and those orders move through statuses: PLACED,
 * PREPARING, OUT_FOR_DELIVERY, DELIVERED, or CANCELED. An {@link Order} has an ID, a restaurant,
 * a customer, a value, a distance in km and a status. This class manages orders and provides
 * order statistics.
 *
 * <p>Part 1: {@code OrderManagerTest} is not passing because of a bug in this class. Read the
 * code, run the test, fix the bug.
 *
 * <p>Part 2: we are adding delivery session information for orders. Implement
 * {@link #addDelivery} and {@link #getAverageDeliveryTimeByRestaurant}. Where the deliveries are
 * stored is up to you.
 *
 * <p>Part 3: we want to know the delivery fee for each order. Implement {@link #getDeliveryFees}.
 */
public class OrderManager {

    private static final double FIRST_KM_FEE = 2.0;
    private static final double EXTRA_KM_FEE = 1.0;
    private static final double FREE_DELIVERY_VALUE = 50.0;
    private static final double HALF_PRICE_VALUE = 30.0;

    public final List<Order> orders = new ArrayList<>();

    /** Deliveries live here, keyed by order ID, so {@link Order} stays a plain data holder. */
    private final Map<Integer, List<Delivery>> deliveriesByOrder = new HashMap<>();

    public void addOrder(Order order) {
        orders.add(order);
    }

    public void updateOrderStatus(int orderId, OrderStatus newStatus) {
        findOrder(orderId).ifPresent(order -> order.status = newStatus);
    }

    /**
     * Returns the number of orders, the number of active orders (not yet delivered or canceled)
     * and the number of closed orders (DELIVERED or CANCELED).
     */
    public OrderStats getOrderStatistics() {
        int total = orders.size();
        int active = 0;
        int closed = 0;
        for (Order order : orders) {
            if (isClosed(order.status)) {
                closed++;
            } else {
                // Bug was leaving OUT_FOR_DELIVERY out of the active statuses.
                active++;
            }
        }
        return new OrderStats(total, active, closed);
    }

    /**
     * Part 2.1: associates a delivery with an order. One order can have multiple deliveries.
     * Returns {@code true} if the delivery was associated; if the order does not exist the
     * delivery is ignored and {@code false} is returned.
     */
    public boolean addDelivery(int orderId, Delivery delivery) {
        if (findOrder(orderId).isEmpty()) {
            return false;
        }
        deliveriesByOrder.computeIfAbsent(orderId, id -> new ArrayList<>()).add(delivery);
        return true;
    }

    /**
     * Part 2.2: the average delivery duration in minutes per restaurant, as a map from restaurant
     * ID to average. Count ALL deliveries for that restaurant, across its orders. A restaurant
     * with no deliveries has no average and is left out.
     */
    public Map<Integer, Double> getAverageDeliveryTimeByRestaurant() {
        Map<Integer, Integer> totals = new LinkedHashMap<>();
        Map<Integer, Integer> counts = new HashMap<>();
        for (Order order : orders) {
            for (Delivery delivery : deliveriesByOrder.getOrDefault(order.orderId, List.of())) {
                totals.merge(order.restaurantId, delivery.getDurationMinutes(), Integer::sum);
                counts.merge(order.restaurantId, 1, Integer::sum);
            }
        }
        Map<Integer, Double> averages = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> entry : totals.entrySet()) {
            int restaurantId = entry.getKey();
            averages.put(restaurantId, (double) entry.getValue() / counts.get(restaurantId));
        }
        return averages;
    }

    /**
     * Part 3: the delivery fee of each order, as a map from order ID to fee. A fee is computed in
     * three steps:
     *
     * <ol>
     *   <li>Round the order's distance UP to the nearest whole km (1.0 is 1, 1.2 is 2, 2.1 is 3).
     *   <li>Base fee: 2 for the first km, plus 1 per extra km (1 km is 2, 2 km is 3, 3 km is 4).
     *   <li>Order value discount: value &gt;= 50 means the fee is 0 (free delivery); value
     *       &gt;= 30 means 50% off the base fee; otherwise the base fee.
     * </ol>
     *
     * Example: distance 2.1 km and order value 35.0: roundedKm = 3, base = 4, 50% off, fee = 2.0.
     */
    public Map<Integer, Double> getDeliveryFees() {
        Map<Integer, Double> fees = new LinkedHashMap<>();
        for (Order order : orders) {
            fees.put(order.orderId, deliveryFee(order));
        }
        return fees;
    }

    private static double deliveryFee(Order order) {
        long roundedKm = (long) Math.ceil(order.distanceKm);
        double baseFee = FIRST_KM_FEE + EXTRA_KM_FEE * (roundedKm - 1);
        if (order.orderValue >= FREE_DELIVERY_VALUE) {
            return 0.0;
        }
        if (order.orderValue >= HALF_PRICE_VALUE) {
            return baseFee / 2; // half of 3 is 1.5, not rounded up
        }
        return baseFee;
    }

    private static boolean isClosed(OrderStatus status) {
        return status == OrderStatus.DELIVERED || status == OrderStatus.CANCELED;
    }

    private Optional<Order> findOrder(int orderId) {
        return orders.stream().filter(order -> order.orderId == orderId).findFirst();
    }
}
