package com.amigoscode.karat.notifications;

/** Where a notification is in the delivery lifecycle. Used from part 2 onward. */
public enum DeliveryStatus {
    QUEUED,
    SENT,
    DELIVERED,
    FAILED
}
