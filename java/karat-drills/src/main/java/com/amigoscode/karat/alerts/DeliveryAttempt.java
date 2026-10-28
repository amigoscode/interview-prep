package com.amigoscode.karat.alerts;

import java.util.Objects;

/** One try to notify an on-call responder about an alert. Used from part 2 onward. */
public class DeliveryAttempt {
    public final String responder;
    public final int timestamp;

    public DeliveryAttempt(String responder, int timestamp) {
        this.responder = responder;
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof DeliveryAttempt a)) {
            return false;
        }
        return responder.equals(a.responder) && timestamp == a.timestamp;
    }

    @Override
    public int hashCode() {
        return Objects.hash(responder, timestamp);
    }

    @Override
    public String toString() {
        return "DeliveryAttempt [" + responder + "] at=" + timestamp;
    }
}
