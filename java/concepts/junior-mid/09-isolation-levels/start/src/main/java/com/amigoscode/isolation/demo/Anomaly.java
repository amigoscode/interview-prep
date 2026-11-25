package com.amigoscode.isolation.demo;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** The things that can go wrong when two transactions overlap. */
public enum Anomaly {
    DIRTY_READ, NON_REPEATABLE_READ, PHANTOM, LOST_UPDATE, WRITE_SKEW;

    /** As written on the command line: "write-skew". */
    public String flag() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    static Anomaly from(String value) {
        return Arrays.stream(values()).filter(anomaly -> anomaly.flag().equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown anomaly: " + value + ". Use one of: " + names()));
    }

    static String names() {
        return Arrays.stream(values()).map(Anomaly::flag).collect(Collectors.joining(" | "));
    }
}
