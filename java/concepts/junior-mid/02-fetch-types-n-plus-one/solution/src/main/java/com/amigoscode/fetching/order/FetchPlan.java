package com.amigoscode.fetching.order;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** The ways GET /api/orders can load the same list: ?fetch=naive, join-fetch, entity-graph or projection. */
public enum FetchPlan {
    NAIVE, JOIN_FETCH, ENTITY_GRAPH, PROJECTION;

    public static class UnknownFetchPlanException extends RuntimeException {
        UnknownFetchPlanException(String value) {
            super("Unknown fetch plan '" + value + "'. Use one of: " + names());
        }
    }

    public static FetchPlan from(String value) {
        for (FetchPlan plan : values()) {
            if (plan.label().equalsIgnoreCase(value)) {
                return plan;
            }
        }
        throw new UnknownFetchPlanException(value);
    }

    /** The name used in the URL: JOIN_FETCH is "join-fetch". */
    public String label() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static String names() {
        return Arrays.stream(values()).map(FetchPlan::label).collect(Collectors.joining(" | "));
    }
}
