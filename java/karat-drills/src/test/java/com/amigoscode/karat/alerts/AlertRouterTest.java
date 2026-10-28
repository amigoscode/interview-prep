package com.amigoscode.karat.alerts;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This is not a complete test suite, but tests some basic functionality of the code and shows how
 * to use it. One test fails on purpose: that is part 1.
 */
class AlertRouterTest {

    @Test
    void alert() {
        Alert a = new Alert(1, "auth", Severity.HIGH, 100);

        assertThat(a.alertId).isEqualTo(1);
        assertThat(a.service).isEqualTo("auth");
        assertThat(a.severity).isEqualTo(Severity.HIGH);
        assertThat(a.createdAt).isEqualTo(100);
        assertThat(a.resolvedAt).isNull();
    }

    @Test
    void hasOpenAlert() {
        AlertRouter router = new AlertRouter();
        router.addAlert(new Alert(1, "auth", Severity.HIGH, 100, 150)); // resolved
        router.addAlert(new Alert(2, "auth", Severity.LOW, 200, null)); // open
        router.addAlert(new Alert(3, "billing", Severity.CRITICAL, 50, 90)); // resolved
        router.addAlert(new Alert(4, "search", Severity.MEDIUM, 10, null)); // open
        // Harder case: "payments" has two resolved alerts before its only open one, with another
        // service's alert mixed in: the open alert is last in the list.
        router.addAlert(new Alert(5, "payments", Severity.CRITICAL, 300, 320)); // resolved
        router.addAlert(new Alert(6, "cdn", Severity.LOW, 10, null)); // different service
        router.addAlert(new Alert(7, "payments", Severity.HIGH, 330, 360)); // resolved
        router.addAlert(new Alert(8, "payments", Severity.MEDIUM, 400, null)); // open

        // "auth" has one resolved and one open alert -> it has an open alert.
        assertThat(router.hasOpenAlert("auth")).isTrue();
        // "billing" has only a resolved alert.
        assertThat(router.hasOpenAlert("billing")).isFalse();
        assertThat(router.hasOpenAlert("search")).isTrue();
        // A service with no alerts at all never has an open alert.
        assertThat(router.hasOpenAlert("unknown")).isFalse();
        // "payments" is open only because of its last alert.
        assertThat(router.hasOpenAlert("payments")).isTrue();
    }

    @Test
    void getAlertReturnsNullForAnUnknownId() {
        AlertRouter router = new AlertRouter();
        Alert alert = new Alert(1, "auth", Severity.HIGH, 100);
        router.addAlert(alert);

        assertThat(router.getAlert(1)).isSameAs(alert);
        assertThat(router.getAlert(2)).isNull();
        assertThat(new AlertRouter().hasOpenAlert("auth")).isFalse();
    }

    @Test
    void resolvingTheOnlyOpenAlertClosesTheService() {
        AlertRouter router = new AlertRouter();
        Alert alert = new Alert(1, "auth", Severity.HIGH, 100);
        router.addAlert(alert);
        assertThat(router.hasOpenAlert("auth")).isTrue();

        alert.resolvedAt = 130;

        assertThat(router.hasOpenAlert("auth")).isFalse();
    }

    @Test
    void recordAttempt() {
        AlertRouter router = new AlertRouter();
        router.addAlert(new Alert(10, "auth", Severity.HIGH, 0, null));
        router.addAlert(new Alert(20, "billing", Severity.LOW, 0, null));

        assertThat(router.recordAttempt(10, new DeliveryAttempt("alice", 5))).isTrue();
        assertThat(router.recordAttempt(10, new DeliveryAttempt("bob", 12))).isTrue();
        assertThat(router.recordAttempt(20, new DeliveryAttempt("alice", 8))).isTrue();
        // No alert with this ID: the attempt should be ignored.
        assertThat(router.recordAttempt(999, new DeliveryAttempt("alice", 9))).isFalse();

        // Edge case: a resolved alert still exists, so recording an attempt against it must still
        // succeed: recordAttempt gates on existence, not on open/resolved.
        router.addAlert(new Alert(30, "search", Severity.CRITICAL, 100, 150)); // resolved
        assertThat(router.recordAttempt(30, new DeliveryAttempt("dave", 200))).isTrue();

        // Recording the same responder again is a separate attempt and still succeeds.
        assertThat(router.recordAttempt(10, new DeliveryAttempt("alice", 40))).isTrue();
    }

    @Test
    void getAttemptCounts() {
        AlertRouter router = new AlertRouter();
        router.addAlert(new Alert(10, "auth", Severity.HIGH, 0, null));
        router.addAlert(new Alert(20, "billing", Severity.LOW, 0, null));
        router.addAlert(new Alert(30, "search", Severity.MEDIUM, 0, null)); // never attempted

        router.recordAttempt(10, new DeliveryAttempt("alice", 5));
        router.recordAttempt(10, new DeliveryAttempt("bob", 12));
        router.recordAttempt(10, new DeliveryAttempt("alice", 20));
        router.recordAttempt(20, new DeliveryAttempt("carol", 8));
        router.recordAttempt(999, new DeliveryAttempt("alice", 9)); // ignored: no such alert

        Map<Integer, Integer> counts = router.getAttemptCounts();

        assertThat(counts).containsOnlyKeys(10, 20);
        assertThat(counts.get(10)).isEqualTo(3);
        assertThat(counts.get(20)).isEqualTo(1);
        assertThat(counts).doesNotContainKey(30);
    }

    @Test
    void emptyRouterHasNoAttemptCounts() {
        AlertRouter router = new AlertRouter();
        assertThat(router.getAttemptCounts()).isEmpty();

        router.addAlert(new Alert(10, "auth", Severity.HIGH, 0, null));
        router.recordAttempt(999, new DeliveryAttempt("alice", 9)); // ignored: no such alert
        assertThat(router.getAttemptCounts()).isEmpty();
    }

    @Test
    void identicalAttemptsAreCountedSeparately() {
        AlertRouter router = new AlertRouter();
        router.addAlert(new Alert(10, "auth", Severity.HIGH, 0, null));

        router.recordAttempt(10, new DeliveryAttempt("alice", 5));
        router.recordAttempt(10, new DeliveryAttempt("alice", 5));

        assertThat(router.getAttemptCounts().get(10)).isEqualTo(2);
    }
}
