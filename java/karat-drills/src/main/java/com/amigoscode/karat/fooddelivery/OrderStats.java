package com.amigoscode.karat.fooddelivery;

/** The result of {@link OrderManager#getOrderStatistics()}. */
public class OrderStats {
    public final int totalOrders;
    public final int activeOrders;
    public final int closedOrders;

    public OrderStats(int totalOrders, int activeOrders, int closedOrders) {
        this.totalOrders = totalOrders;
        this.activeOrders = activeOrders;
        this.closedOrders = closedOrders;
    }
}
