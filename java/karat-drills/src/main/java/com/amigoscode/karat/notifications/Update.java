package com.amigoscode.karat.notifications;

import java.util.Objects;

/**
 * One delivery update reported by the provider: a (status, timestamp) pair. The timestamp is in
 * minutes since the start of the window. Two updates with the same status and timestamp are equal.
 */
public class Update {
    private final DeliveryStatus status;
    private final int timestamp;

    public Update(DeliveryStatus status, int timestamp) {
        this.status = status;
        this.timestamp = timestamp;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public int getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Update u)) {
            return false;
        }
        return status == u.status && timestamp == u.timestamp;
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, timestamp);
    }

    @Override
    public String toString() {
        return "Update " + status + " at=" + timestamp;
    }
}
