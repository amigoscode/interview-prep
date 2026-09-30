package com.amigoscode.dispatch;

import java.util.Objects;

/** A rider and their last known location. */
public record Rider(String id, Location location) {

    public Rider {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("rider id must not be blank");
        }
        Objects.requireNonNull(location, "location");
    }
}
