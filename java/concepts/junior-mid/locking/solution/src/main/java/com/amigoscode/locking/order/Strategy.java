package com.amigoscode.locking.order;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** How an order protects the stock from a concurrent buyer. Chosen per request with ?strategy=. */
public enum Strategy {
    NAIVE,
    PESSIMISTIC,
    OPTIMISTIC,
    ATOMIC;

    public static Strategy from(String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new UnknownStrategyException(value);
        }
    }

    public static String names() {
        return Arrays.stream(values())
                .map(strategy -> strategy.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(", "));
    }

    public static class UnknownStrategyException extends RuntimeException {

        public UnknownStrategyException(String value) {
            super("Unknown strategy '" + value + "'. Use one of: " + names());
        }
    }
}
