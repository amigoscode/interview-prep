package com.amigoscode.karat.compute;

/**
 * The state of a reservation. Only ACTIVE reservations hold capacity; a CANCELLED or EXPIRED
 * reservation stays in the history but no longer consumes anything.
 */
public enum ReservationStatus {
    ACTIVE,
    CANCELLED,
    EXPIRED
}
