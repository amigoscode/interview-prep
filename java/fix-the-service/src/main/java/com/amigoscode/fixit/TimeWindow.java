package com.amigoscode.fixit;

import java.time.LocalTime;
import java.util.Objects;

/** A daily window of time, start included and end excluded: [start, end). */
public record TimeWindow(LocalTime start, LocalTime end) {

    public TimeWindow {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("start must be before end: " + start + " - " + end);
        }
    }

    public static TimeWindow between(int startHour, int endHour) {
        return new TimeWindow(LocalTime.of(startHour, 0), LocalTime.of(endHour, 0));
    }

    public boolean contains(LocalTime time) {
        return !time.isBefore(start) && time.isBefore(end);
    }
}
