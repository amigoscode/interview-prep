package com.amigoscode.interview.flight;

import java.time.LocalTime;

/**
 * The incoming flight plan, as described in the assessment brief.
 *
 * <p>The types are boxed on purpose: a field missing from the JSON arrives as {@code null} rather
 * than silently becoming {@code 0}. What you do about that is up to you.
 */
public record FlightPlanRequest(
        String flightNumber,
        LocalTime takeOffTime,
        Integer passengers,
        Double departureLatitude,
        Double departureLongitude,
        Double arrivalLatitude,
        Double arrivalLongitude) {
}
