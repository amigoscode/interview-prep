package com.amigoscode.karat.equipment;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This is not a complete test suite, but tests some basic functionality of the code and shows how
 * to use it. Two tests fail on purpose: that is part 1.
 */
@Disabled("equipment drill: remove this line to begin part 1")
class FacilityManagerTest {

    @Test
    void testMakeReservation() {
        FacilityManager manager = new FacilityManager();
        Reservation res = new Reservation(101, "Alice Johnson", 1, 480, 600);

        assertThat(manager.makeReservation(res)).isTrue();
        assertThat(manager.reservations).hasSize(1);
    }

    @Test
    void testConflictDetected() {
        FacilityManager manager = new FacilityManager();
        Reservation res1 = new Reservation(101, "Alice Johnson", 1, 480, 600);
        Reservation res2 = new Reservation(102, "Bob Smith", 1, 540, 660);

        manager.makeReservation(res1);
        assertThat(manager.makeReservation(res2)).isFalse();
    }

    @Test
    void testDifferentEquipmentNoConflict() {
        FacilityManager manager = new FacilityManager();
        Reservation res1 = new Reservation(101, "Alice Johnson", 1, 480, 600);
        Reservation res2 = new Reservation(102, "Bob Smith", 2, 480, 600);

        assertThat(manager.makeReservation(res1)).isTrue();
        assertThat(manager.makeReservation(res2)).isTrue();
    }

    @Test
    void testCancelledReservationFreesSlot() {
        FacilityManager manager = new FacilityManager();
        Reservation res1 = new Reservation(101, "Alice Johnson", 1, 480, 600);
        manager.makeReservation(res1);

        assertThat(manager.cancelReservation(101)).isTrue();

        // After cancellation, the same slot should be bookable again
        Reservation res2 = new Reservation(102, "Bob Smith", 1, 480, 600);
        assertThat(manager.makeReservation(res2)).isTrue();
    }

    @Test
    void testCancelReservationSuccess() {
        FacilityManager manager = new FacilityManager();
        Reservation res = new Reservation(101, "Alice Johnson", 1, 480, 600);
        manager.makeReservation(res);

        // Cancellation returns true and status is updated to CANCELLED
        assertThat(manager.cancelReservation(101)).isTrue();
        assertThat(manager.reservations.get(0).getStatus()).isEqualTo(ReservationStatus.CANCELLED);

        // Cancelled reservation remains in the collection, it is not removed
        assertThat(manager.reservations).hasSize(1);

        // Other fields are unchanged after cancellation
        assertThat(manager.reservations.get(0).getReservationId()).isEqualTo(101);
        assertThat(manager.reservations.get(0).getStartTime()).isEqualTo(480);
        assertThat(manager.reservations.get(0).getEndTime()).isEqualTo(600);
        assertThat(manager.reservations.get(0).getEquipmentId()).isEqualTo(1);
        assertThat(manager.reservations.get(0).getMemberName()).isEqualTo("Alice Johnson");

        // Cancelling a non-existent reservation returns false
        assertThat(manager.cancelReservation(999)).isFalse();
    }

    @Test
    @Disabled("part 2: remove this line when part 1 is green")
    void testGetActiveReservationIds() {
        FacilityManager manager = new FacilityManager();

        // No reservations
        assertThat(manager.getActiveReservationIds()).isEmpty();

        Reservation res1 = new Reservation(103, "Carol White", 1, 600, 690);
        Reservation res2 = new Reservation(101, "Alice Johnson", 1, 480, 540);
        Reservation res3 = new Reservation(102, "Bob Smith", 2, 500, 560);
        manager.makeReservation(res1);
        manager.makeReservation(res2);
        manager.makeReservation(res3);
        assertThat(manager.getActiveReservationIds()).containsExactly(101, 102, 103);

        manager.cancelReservation(101);
        assertThat(manager.getActiveReservationIds()).containsExactly(102, 103);

        manager.cancelReservation(103);
        assertThat(manager.getActiveReservationIds()).containsExactly(102);
    }

    @Test
    @Disabled("part 2: remove this line when part 1 is green")
    void testGetTotalMinutesByStatus() {
        FacilityManager manager = new FacilityManager();

        // No reservations: both keys still present
        Map<String, Integer> totals = manager.getTotalMinutesByStatus();
        assertThat(totals).containsOnlyKeys("ACTIVE", "CANCELLED");
        assertThat(totals.get("ACTIVE")).isEqualTo(0);
        assertThat(totals.get("CANCELLED")).isEqualTo(0);

        Reservation res1 = new Reservation(101, "Alice Johnson", 1, 480, 540); // 60 min
        Reservation res2 = new Reservation(102, "Bob Smith", 2, 500, 560);     // 60 min
        Reservation res3 = new Reservation(103, "Carol White", 1, 600, 690);   // 90 min
        manager.makeReservation(res1);
        manager.makeReservation(res2);
        manager.makeReservation(res3);

        totals = manager.getTotalMinutesByStatus();
        assertThat(totals.get("ACTIVE")).isEqualTo(210);
        assertThat(totals.get("CANCELLED")).isEqualTo(0);

        manager.cancelReservation(101);
        totals = manager.getTotalMinutesByStatus();
        assertThat(totals.get("ACTIVE")).isEqualTo(150);
        assertThat(totals.get("CANCELLED")).isEqualTo(60);

        manager.cancelReservation(102);
        manager.cancelReservation(103);
        totals = manager.getTotalMinutesByStatus();
        assertThat(totals.get("ACTIVE")).isEqualTo(0);
        assertThat(totals.get("CANCELLED")).isEqualTo(210);
    }
}
