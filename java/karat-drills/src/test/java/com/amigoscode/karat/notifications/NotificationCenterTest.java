package com.amigoscode.karat.notifications;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This is not a complete test suite, but tests some basic functionality of the code and shows how
 * to use it. One test fails on purpose: that is part 1.
 */
class NotificationCenterTest {

    @Test
    void notification() {
        Notification n = new Notification(1, 500, 30);

        assertThat(n.getNotificationId()).isEqualTo(1);
        assertThat(n.getOrderId()).isEqualTo(500);
        assertThat(n.getCreatedAt()).isEqualTo(30);
    }

    @Test
    void getOrderNotifications() {
        NotificationCenter center = new NotificationCenter();
        center.addNotification(new Notification(1, 1, 0));
        center.addNotification(new Notification(2, 2, 0));
        center.addNotification(new Notification(3, 1, 0));
        center.addNotification(new Notification(4, 2, 0));
        center.addNotification(new Notification(5, 1, 0));

        // Order 1 owns notifications 1, 3, and 5.
        assertThat(center.getOrderNotifications(1)).containsExactly(1, 3, 5);
        // Order 2 owns notifications 2 and 4.
        assertThat(center.getOrderNotifications(2)).containsExactly(2, 4);
        // An order with no notifications returns an empty list.
        assertThat(center.getOrderNotifications(99)).isEmpty();
    }

    @Test
    void getNotificationReturnsNullForAnUnknownId() {
        NotificationCenter center = new NotificationCenter();
        Notification notification = new Notification(7, 300, 12);
        center.addNotification(notification);

        assertThat(center.getNotification(7)).isSameAs(notification);
        assertThat(center.getNotification(8)).isNull();
        assertThat(new NotificationCenter().getOrderNotifications(300)).isEmpty();
    }

    @Test
    void addUpdates() {
        NotificationCenter center = new NotificationCenter();
        center.addNotification(new Notification(40, 100, 0));

        // The first batch stores both pairs.
        assertThat(center.addUpdates(40, List.of(
                new Update(DeliveryStatus.SENT, 5),
                new Update(DeliveryStatus.DELIVERED, 15)))).isEqualTo(2);
        // (SENT, 5) is already stored, so only (FAILED, 30) is new.
        assertThat(center.addUpdates(40, List.of(
                new Update(DeliveryStatus.SENT, 5),
                new Update(DeliveryStatus.FAILED, 30)))).isEqualTo(1);
        assertThat(center.getUpdateCount(40)).isEqualTo(3);

        // No notification with this ID: nothing is stored.
        assertThat(center.addUpdates(41, List.of(new Update(DeliveryStatus.QUEUED, 1)))).isZero();
        assertThat(center.getUpdateCount(41)).isZero();
    }

    @Test
    void getUpdateCount() {
        NotificationCenter center = new NotificationCenter();
        center.addNotification(new Notification(40, 100, 0));
        center.addNotification(new Notification(41, 100, 2));

        // A notification with no updates, and an unknown notification, both count 0.
        assertThat(center.getUpdateCount(40)).isZero();
        assertThat(center.getUpdateCount(999)).isZero();

        // A pair repeated inside one batch is stored once.
        assertThat(center.addUpdates(40, List.of(
                new Update(DeliveryStatus.QUEUED, 1),
                new Update(DeliveryStatus.QUEUED, 1),
                new Update(DeliveryStatus.SENT, 4)))).isEqualTo(2);
        assertThat(center.getUpdateCount(40)).isEqualTo(2);
        // Updates are kept per notification.
        assertThat(center.getUpdateCount(41)).isZero();
    }

    @Test
    void emptyBatchStoresNothing() {
        NotificationCenter center = new NotificationCenter();
        center.addNotification(new Notification(40, 100, 0));

        assertThat(center.addUpdates(40, List.of())).isZero();
        assertThat(center.getUpdateCount(40)).isZero();
    }

    @Test
    void sameTimestampWithAnotherStatusIsNotADuplicate() {
        NotificationCenter center = new NotificationCenter();
        center.addNotification(new Notification(40, 100, 0));
        center.addUpdates(40, List.of(new Update(DeliveryStatus.SENT, 5)));

        assertThat(center.addUpdates(40, List.of(
                new Update(DeliveryStatus.DELIVERED, 5),
                new Update(DeliveryStatus.SENT, 6)))).isEqualTo(2);
        assertThat(center.getUpdateCount(40)).isEqualTo(3);
    }
}
