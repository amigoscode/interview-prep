package com.amigoscode.isolation.demo;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** The four isolation levels of the SQL standard, weakest first. */
public enum Level {
    READ_UNCOMMITTED, READ_COMMITTED, REPEATABLE_READ, SERIALIZABLE;

    /** As written in SQL: "repeatable read". */
    public String sql() {
        return name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    /** As written on the command line: "repeatable-read". */
    public String flag() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    static Level from(String value) {
        return Arrays.stream(values()).filter(level -> level.flag().equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown level: " + value + ". Use one of: " + names()));
    }

    static String names() {
        return Arrays.stream(values()).map(Level::flag).collect(Collectors.joining(" | "));
    }
}
