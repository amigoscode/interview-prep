package com.amigoscode.karat.fooddelivery;

/** An order a customer placed at a restaurant. */
public class Order {
    public final int orderId;
    public final int restaurantId;
    public final int customerId;
    public final double orderValue;
    public final double distanceKm;
    public OrderStatus status;

    public Order(int orderId, int restaurantId, int customerId, double orderValue,
                 double distanceKm, OrderStatus status) {
        this.orderId = orderId;
        this.restaurantId = restaurantId;
        this.customerId = customerId;
        this.orderValue = orderValue;
        this.distanceKm = distanceKm;
        this.status = status;
    }
}
