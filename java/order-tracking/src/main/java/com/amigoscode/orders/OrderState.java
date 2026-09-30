package com.amigoscode.orders;

/**
 * RECEIVED -> ACCEPTED -> PICKED_UP -> DELIVERED, plus CANCELLED from RECEIVED or ACCEPTED only.
 * Where the transition rules live is up to you.
 */
public enum OrderState {
    RECEIVED, ACCEPTED, PICKED_UP, DELIVERED, CANCELLED
}
