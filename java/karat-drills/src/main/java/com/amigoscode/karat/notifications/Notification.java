package com.amigoscode.karat.notifications;

import java.util.Objects;

/**
 * Data about a single notification created for an order. createdAt is in minutes since the start
 * of the current window.
 */
public class Notification {
    private final int notificationId;
    private final int orderId;
    private final int createdAt;

    public Notification(int notificationId, int orderId, int createdAt) {
        this.notificationId = notificationId;
        this.orderId = orderId;
        this.createdAt = createdAt;
    }

    public int getNotificationId() {
        return notificationId;
    }

    public int getOrderId() {
        return orderId;
    }

    public int getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Notification n)) {
            return false;
        }
        return notificationId == n.notificationId
                && orderId == n.orderId
                && createdAt == n.createdAt;
    }

    @Override
    public int hashCode() {
        return Objects.hash(notificationId, orderId, createdAt);
    }

    @Override
    public String toString() {
        return "Notification " + notificationId + " (order " + orderId + ") created=" + createdAt;
    }
}
