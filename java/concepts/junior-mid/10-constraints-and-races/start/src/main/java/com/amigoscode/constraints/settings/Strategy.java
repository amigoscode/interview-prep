package com.amigoscode.constraints.settings;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** How "give me this user's settings, create them if they do not exist yet" is done. Chosen with ?strategy=. */
public enum Strategy {
    CHECK_FIRST,
    CATCH_INSIDE,
    CATCH_OUTSIDE,
    SAVEPOINT,
    ON_CONFLICT;

    public static Strategy from(String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new UnknownStrategyException(value);
        }
    }

    public String label() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static String names() {
        return Arrays.stream(values()).map(Strategy::label).collect(Collectors.joining(", "));
    }

    public static class UnknownStrategyException extends RuntimeException {

        public UnknownStrategyException(String value) {
            super("Unknown strategy '" + value + "'. Use one of: " + names());
        }
    }
}
