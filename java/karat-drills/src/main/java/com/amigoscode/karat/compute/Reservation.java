package com.amigoscode.karat.compute;

/**
 * A customer's claim on some capacity units of one pool between a start and an end time. Times
 * are plain numbers; what unit they are in does not matter for this exercise.
 */
public class Reservation {
    public int reservationId;
    public int customerId;
    public int poolId;
    public int capacityUnits;
    public ReservationStatus status;
    public int startTime;
    public int endTime;

    public Reservation(
            int reservationId,
            int customerId,
            int poolId,
            int capacityUnits,
            ReservationStatus status,
            int startTime,
            int endTime) {
        this.reservationId = reservationId;
        this.customerId = customerId;
        this.poolId = poolId;
        this.capacityUnits = capacityUnits;
        this.status = status;
        this.startTime = startTime;
        this.endTime = endTime;
    }
}
