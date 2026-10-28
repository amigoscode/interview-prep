package com.amigoscode.karat.alerts;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * We are building the backend for an incident alerting service. Each Alert is a problem raised
 * against a monitored service: it has an alertId, a service name, a severity, a createdAt time and
 * a resolvedAt time (null while the alert is open). Times are minutes since the start of the
 * current on-call window.
 *
 * <p>Part 1: {@code AlertRouterTest} is not passing because of a bug in this class. Read the
 * code, run the test, fix the bug.
 *
 * <p>Part 2: we are tracking delivery attempts, each one a {@link DeliveryAttempt} with a
 * responder and a timestamp. Implement {@link #recordAttempt} and {@link #getAttemptCounts}.
 * Where the attempts are stored is up to you.
 */
public class AlertRouter {

    private final List<Alert> alerts = new ArrayList<>();

    /** Registers an alert for the current on-call window. */
    public void addAlert(Alert alert) {
        alerts.add(alert);
    }

    /** Returns the alert with the given ID, or null if there is no such alert. */
    public Alert getAlert(int alertId) {
        for (Alert alert : alerts) {
            if (alert.alertId == alertId) {
                return alert;
            }
        }
        return null;
    }

    /** Returns whether the given service currently has at least one open alert. */
    public boolean hasOpenAlert(String service) {
        for (Alert alert : alerts) {
            if (alert.service.equals(service)) {
                return alert.resolvedAt == null;
            }
        }
        return false;
    }

    /**
     * Part 2.1: associates the attempt with the alert and returns true. If there is no alert with
     * the given ID the attempt is ignored and false is returned. A resolved alert still accepts
     * attempts, and the same responder can be recorded any number of times.
     */
    public boolean recordAttempt(int alertId, DeliveryAttempt attempt) {
        throw new UnsupportedOperationException("part 2");
    }

    /**
     * Part 2.2: a map from alert ID to the number of delivery attempts recorded for it. Only
     * alerts with at least one attempt appear; alerts with none are omitted.
     */
    public Map<Integer, Integer> getAttemptCounts() {
        throw new UnsupportedOperationException("part 2");
    }
}
