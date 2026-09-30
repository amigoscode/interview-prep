package com.amigoscode.orders;

import java.time.Instant;
import java.util.Objects;

/** The order header every event carries, so any event can be the first one we see. */
public record OrderDetails(String restaurantId, String zoneId, Instant placedAt, Instant promisedBy) {

    public OrderDetails {
        requireText(restaurantId, "restaurantId");
        requireText(zoneId, "zoneId");
        Objects.requireNonNull(placedAt, "placedAt");
        Objects.requireNonNull(promisedBy, "promisedBy");
        if (promisedBy.isBefore(placedAt)) {
            throw new IllegalArgumentException("promisedBy must not be before placedAt");
        }
    }

    static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
