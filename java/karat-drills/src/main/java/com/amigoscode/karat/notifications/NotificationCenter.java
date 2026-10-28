package com.amigoscode.karat.notifications;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * We are building the backend for a retail order notification service. When something happens to
 * a customer's order the service creates a Notification for that order: it has a notificationId,
 * an orderId and a createdAt time (minutes since the start of the current window). One order can
 * have many notifications.
 *
 * <p>Part 1: {@code NotificationCenterTest} is not passing because of a bug in this class. Read
 * the code, run the test, fix the bug.
 *
 * <p>Part 2: delivery updates for a notification arrive from a provider, sometimes in batches,
 * and the same update can be reported more than once. Implement {@link #addUpdates} and
 * {@link #getUpdateCount}. Where the updates are stored is up to you.
 */
public class NotificationCenter {

    private final List<Notification> notifications = new ArrayList<>();

    /** Registers a notification for the current window. */
    public void addNotification(Notification notification) {
        notifications.add(notification);
    }

    /** Returns the notification with the given ID, or null if there is no such notification. */
    public Notification getNotification(int notificationId) {
        for (Notification notification : notifications) {
            if (notification.getNotificationId() == notificationId) {
                return notification;
            }
        }
        return null;
    }

    /** Returns the IDs of all notifications for the given order, sorted in ascending order. */
    public List<Integer> getOrderNotifications(int orderId) {
        List<Integer> result = new ArrayList<>();
        for (Notification notification : notifications) {
            if (notification.getOrderId() == orderId) {
                result.add(notification.getOrderId());
            }
        }
        Collections.sort(result);
        return result;
    }

    /**
     * Part 2.1: {@code updates} is a list of (status, timestamp) pairs for one notification. If
     * the notification exists, stores the pairs not already stored and returns how many were newly
     * stored; otherwise stores nothing and returns 0. A pair is a duplicate if the same status and
     * timestamp is already stored, whether from an earlier batch or earlier in the same batch.
     */
    public int addUpdates(int notificationId, List<Update> updates) {
        throw new UnsupportedOperationException("part 2");
    }

    /**
     * Part 2.2: how many updates are currently stored for the notification: 0 if it has none, or
     * if there is no such notification.
     */
    public int getUpdateCount(int notificationId) {
        throw new UnsupportedOperationException("part 2");
    }
}
