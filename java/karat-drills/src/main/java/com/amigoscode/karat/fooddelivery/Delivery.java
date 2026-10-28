package com.amigoscode.karat.fooddelivery;

/**
 * A delivery session for an order. Start and end are minutes from the start of the day; all
 * deliveries are on the same day.
 */
public class Delivery {
    public final int deliveryId;
    public final int startMinute;
    public final int endMinute;

    public Delivery(int deliveryId, int startMinute, int endMinute) {
        this.deliveryId = deliveryId;
        this.startMinute = startMinute;
        this.endMinute = endMinute;
    }

    public int getDurationMinutes() {
        return endMinute - startMinute;
    }
}
