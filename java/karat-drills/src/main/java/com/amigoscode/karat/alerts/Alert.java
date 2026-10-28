package com.amigoscode.karat.alerts;

import java.util.Objects;

/**
 * A single reported problem for a service. Times are minutes since the start of the on-call
 * window. An alert is open while resolvedAt is null.
 */
public class Alert {
    public final int alertId;
    public final String service;
    public final Severity severity;
    public final int createdAt;
    public Integer resolvedAt;

    public Alert(int alertId, String service, Severity severity, int createdAt) {
        this(alertId, service, severity, createdAt, null);
    }

    public Alert(int alertId, String service, Severity severity, int createdAt, Integer resolvedAt) {
        this.alertId = alertId;
        this.service = service;
        this.severity = severity;
        this.createdAt = createdAt;
        this.resolvedAt = resolvedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Alert a)) {
            return false;
        }
        return alertId == a.alertId
                && service.equals(a.service)
                && severity == a.severity
                && createdAt == a.createdAt
                && Objects.equals(resolvedAt, a.resolvedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(alertId, service, severity, createdAt, resolvedAt);
    }

    @Override
    public String toString() {
        return "Alert " + alertId + " [" + service + "] " + severity
                + " created=" + createdAt + " resolved=" + resolvedAt;
    }
}
