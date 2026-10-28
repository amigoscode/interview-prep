package com.amigoscode.karat.alerts;

/** Severity is ranked: LOW is the lowest, then MEDIUM, then HIGH, then CRITICAL. */
public enum Severity {
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    CRITICAL(4);

    private final int rank;

    Severity(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }
}
