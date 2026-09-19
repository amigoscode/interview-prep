package com.amigoscode.interview.flight;

import java.util.List;

/**
 * The answer the brief asks for: whether the flight is feasible, and feedback explaining which
 * rules were violated.
 *
 * @param feasible whether the flight plan passed every rule
 * @param feedback messages explaining the violations, empty when the flight is feasible
 */
public record FeasibilityResponse(boolean feasible, List<String> feedback) {
}
