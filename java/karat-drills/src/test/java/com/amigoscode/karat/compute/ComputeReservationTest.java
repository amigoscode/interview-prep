package com.amigoscode.karat.compute;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This is not a complete test suite, but tests some basic functionality of the code and shows how
 * to use it. One test fails on purpose: that is part 1.
 */
class ComputeReservationTest {

    @Test
    void availableCapacity() {
        ReservationManager manager = new ReservationManager();
        manager.addComputePool(new ComputePool(1, "us-east", 100));
        manager.reservations.add(new Reservation(101, 1001, 1, 30, ReservationStatus.ACTIVE, 1000, 2000));
        manager.reservations.add(new Reservation(102, 1002, 1, 20, ReservationStatus.CANCELLED, 1000, 2000));
        manager.reservations.add(new Reservation(103, 1003, 1, 20, ReservationStatus.EXPIRED, 1000, 2000));

        assertThat(manager.getAvailableCapacity(1)).isEqualTo(70);
        assertThat(manager.getAvailableCapacity(99)).isEqualTo(0); // unknown pool
    }

    @Test
    void poolWithNoReservationsHasAllOfItsCapacity() {
        ReservationManager manager = new ReservationManager();
        manager.addComputePool(new ComputePool(1, "us-east", 100));
        manager.addComputePool(new ComputePool(2, "eu-west", 40));
        manager.reservations.add(new Reservation(101, 1001, 1, 30, ReservationStatus.ACTIVE, 1000, 2000));

        assertThat(manager.getAvailableCapacity(2)).isEqualTo(40); // pool 1's reservation does not count
    }

    @Test
    void addReservation() {
        ReservationManager manager = new ReservationManager();
        manager.addComputePool(new ComputePool(1, "us-east", 100));
        manager.reservations.add(new Reservation(201, 1001, 1, 50, ReservationStatus.CANCELLED, 1000, 2000));
        manager.addReservation(new Reservation(202, 1002, 1, 30, ReservationStatus.ACTIVE, 1000, 2000));
        manager.reservations.add(new Reservation(203, 1003, 1, 40, ReservationStatus.EXPIRED, 1000, 2000));
        manager.addReservation(new Reservation(204, 1004, 1, 60, ReservationStatus.ACTIVE, 1000, 2000));
        manager.addReservation(new Reservation(205, 1005, 99, 20, ReservationStatus.ACTIVE, 1000, 2000)); // unknown pool
        manager.addReservation(new Reservation(206, 1006, 1, 50, ReservationStatus.ACTIVE, 1000, 2000)); // only 10 left

        assertThat(manager.reservations).hasSize(4);
        assertThat(manager.reservations.get(3).reservationId).isEqualTo(204);
    }

    @Test
    void reservationThatExactlyFillsThePoolIsStored() {
        ReservationManager manager = new ReservationManager();
        manager.addComputePool(new ComputePool(1, "us-east", 100));
        manager.addReservation(new Reservation(201, 1001, 1, 60, ReservationStatus.ACTIVE, 1000, 2000));

        manager.addReservation(new Reservation(202, 1002, 1, 40, ReservationStatus.ACTIVE, 1000, 2000)); // 40 <= 40
        manager.addReservation(new Reservation(203, 1003, 1, 1, ReservationStatus.ACTIVE, 1000, 2000)); // pool is full

        assertThat(manager.reservations).hasSize(2);
        assertThat(manager.getAvailableCapacity(1)).isZero();
    }

    @Test
    void reservedCapacityByCustomer() {
        ReservationManager manager = new ReservationManager();
        manager.addComputePool(new ComputePool(1, "us-east", 200));
        manager.addReservation(new Reservation(301, 1001, 1, 30, ReservationStatus.ACTIVE, 1000, 2000));
        manager.addReservation(new Reservation(302, 1001, 1, 20, ReservationStatus.ACTIVE, 1000, 2000));
        manager.addReservation(new Reservation(303, 1002, 1, 10, ReservationStatus.ACTIVE, 1000, 2000));
        manager.addReservation(new Reservation(304, 1003, 1, 50, ReservationStatus.CANCELLED, 1000, 2000));
        manager.addReservation(new Reservation(305, 1004, 1, 0, ReservationStatus.ACTIVE, 1000, 2000));
        manager.addReservation(new Reservation(306, 1005, 1, 25, ReservationStatus.EXPIRED, 1000, 2000));

        Map<Integer, Integer> result = manager.getReservedCapacityByCustomer();

        assertThat(result).hasSize(2);
        assertThat(result).containsEntry(1001, 50);
        assertThat(result).containsEntry(1002, 10);
        assertThat(result).doesNotContainKey(1003);
        assertThat(result).doesNotContainKey(1004);
        assertThat(result).doesNotContainKey(1005);
    }

    @Test
    void reservedCapacityIsSummedAcrossPools() {
        ReservationManager manager = new ReservationManager();
        manager.addComputePool(new ComputePool(1, "us-east", 100));
        manager.addComputePool(new ComputePool(2, "eu-west", 100));
        manager.addReservation(new Reservation(401, 1001, 1, 30, ReservationStatus.ACTIVE, 1000, 2000));
        manager.addReservation(new Reservation(402, 1001, 2, 45, ReservationStatus.ACTIVE, 1000, 2000));

        assertThat(manager.getReservedCapacityByCustomer()).containsOnlyKeys(1001);
        assertThat(manager.getReservedCapacityByCustomer().get(1001)).isEqualTo(75);
        assertThat(new ReservationManager().getReservedCapacityByCustomer()).isEmpty();
    }
}
