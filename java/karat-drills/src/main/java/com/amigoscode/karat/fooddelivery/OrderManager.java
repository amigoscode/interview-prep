package com.amigoscode.karat.fooddelivery;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    public final List<Order> orders = new ArrayList<>();

    public void addOrder(Order order) {
        orders.add(order);
    }

    public void updateOrderStatus(int orderId, OrderStatus newStatus) {
        for (Order order : orders) {
            if (order.orderId == orderId) {
                order.status = newStatus;
                return;
            }
        }
    }

    /**
     * Returns the number of orders, the number of active orders (not yet delivered or canceled)
     * and the number of closed orders (DELIVERED or CANCELED).
     */
    public OrderStats getOrderStatistics() {
        int total = orders.size();
        int active = 0;
        for (Order order : orders) {
            if (order.status == OrderStatus.PLACED || order.status == OrderStatus.PREPARING) {
                active++;
            }
        }
        int closed = 0;
        for (Order order : orders) {
            if (order.status == OrderStatus.DELIVERED || order.status == OrderStatus.CANCELED) {
                closed++;
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
        throw new UnsupportedOperationException("part 2");
    }

    /**
     * Part 2.2: the average delivery duration in minutes per restaurant, as a map from restaurant
     * ID to average. Count ALL deliveries for that restaurant, across its orders. A restaurant
     * with no deliveries has no average and is left out.
     */
    public Map<Integer, Double> getAverageDeliveryTimeByRestaurant() {
        throw new UnsupportedOperationException("part 2");
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
        throw new UnsupportedOperationException("part 3");
    }
}
