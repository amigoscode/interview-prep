package com.amigoscode.constraints.subscription;

import java.util.Locale;

public enum Plan {
    BASIC(900),
    PRO(2900);

    private final int priceCents;

    Plan(int priceCents) {
        this.priceCents = priceCents;
    }

    /** Monthly price of one seat, in cents. */
    public int priceCents() {
        return priceCents;
    }

    public static Plan from(String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Unknown plan '" + value + "'. Use one of: basic, pro");
        }
    }
}
