package com.amigoscode.karat.equipment;

import java.util.Objects;

/**
 * A member's booking of one piece of equipment over a time period. Times are minutes from the
 * start of the day. A new reservation is ACTIVE; two reservations are equal when their IDs are.
 */
public class Reservation {
    private final int reservationId;
    private final String memberName;
    private final int equipmentId;
    private final int startTime;
    private final int endTime;
    private ReservationStatus status;

    public Reservation(int reservationId, String memberName, int equipmentId, int startTime, int endTime) {
        this.reservationId = reservationId;
        this.memberName = memberName;
        this.equipmentId = equipmentId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = ReservationStatus.ACTIVE;
    }

    public int getReservationId() {
        return reservationId;
    }

    public String getMemberName() {
        return memberName;
    }

    public int getEquipmentId() {
        return equipmentId;
    }

    public int getStartTime() {
        return startTime;
    }

    public int getEndTime() {
        return endTime;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }

    public int getDuration() {
        return endTime - startTime;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Reservation r)) {
            return false;
        }
        return reservationId == r.reservationId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(reservationId);
    }

    @Override
    public String toString() {
        return "Reservation ID: " + reservationId
                + ", Member: " + memberName
                + ", Equipment ID: " + equipmentId
                + ", Start: " + startTime
                + ", End: " + endTime
                + ", Status: " + status;
    }
}
