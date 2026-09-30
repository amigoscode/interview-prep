package com.amigoscode.arrays;

import java.util.Objects;

/** One rider leg: the rider rode from {@code from} to {@code to}. City names match exactly. */
public record Leg(String from, String to) {

    public Leg {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
    }
}
