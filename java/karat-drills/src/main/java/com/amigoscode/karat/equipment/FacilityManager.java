package com.amigoscode.karat.equipment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * We are building a program to manage equipment bookings at a sports facility. The facility has
 * multiple pieces of equipment, such as treadmills and rowing machines, and members can reserve
 * them for specific time periods. Two reservations conflict when they overlap in time on the
 * same equipment.
 *
 * <p>Part 1: {@code FacilityManagerTest} is not passing because of a bug in this class. Read the
 * code, run the tests, fix the bug.
 *
 * <p>Part 2: we are adding reservation status reporting. Implement
 * {@link #getActiveReservationIds} and {@link #getTotalMinutesByStatus}.
 */
public class FacilityManager {

    public final List<Equipment> equipmentList = new ArrayList<>();
    public final List<Reservation> reservations = new ArrayList<>();

    public void addEquipment(Equipment equipment) {
        equipmentList.add(equipment);
    }

    /**
     * Makes a reservation if the equipment is available for the requested period. Returns true if
     * successfully reserved, false if the equipment is unavailable.
     */
    public boolean makeReservation(Reservation reservation) {
        if (!isAvailable(
                reservation.getEquipmentId(),
                reservation.getStartTime(),
                reservation.getEndTime())) {
            return false;
        }
        reservations.add(reservation);
        return true;
    }

    private boolean isAvailable(int equipmentId, int startTime, int endTime) {
        for (Reservation res : reservations) {
            if (res.getEquipmentId() == equipmentId) {
                if (startTime < res.getEndTime() && endTime > res.getStartTime()) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Cancels a reservation by ID. Returns true if found and cancelled, false otherwise. */
    public boolean cancelReservation(int reservationId) {
        for (Reservation res : reservations) {
            if (res.getReservationId() == reservationId) {
                res.setStatus(ReservationStatus.CANCELLED);
                return false;
            }
        }
        return true;
    }

    /**
     * Part 2.1: returns a sorted list of reservation IDs for all ACTIVE reservations. If there
     * are no active reservations, returns an empty list.
     */
    public List<Integer> getActiveReservationIds() {
        throw new UnsupportedOperationException("part 2");
    }

    /**
     * Part 2.2: returns a map with two keys: "ACTIVE", the total duration in minutes across all
     * active reservations, and "CANCELLED", the total duration in minutes across all cancelled
     * reservations. Both keys are always present, even if the total is 0.
     */
    public Map<String, Integer> getTotalMinutesByStatus() {
        throw new UnsupportedOperationException("part 2");
    }
}
