package com.amigoscode.karat.access;

/**
 * Access levels are ranked: VISITOR is the lowest, STAFF is higher and ADMIN is the highest.
 * VISITOR is the default access level a new user receives.
 */
public enum AccessLevel {
    VISITOR(1),
    STAFF(2),
    ADMIN(3);

    private final int rank;

    AccessLevel(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }
}
