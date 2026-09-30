package com.amigoscode.dispatch;

import java.time.LocalTime;
import java.util.Objects;

/**
 * A shift within one day, half-open: {@code [start, end)}. A rider is on shift at {@code start} and
 * off shift at {@code end}. Overnight shifts are two shifts: 22:00-{@link LocalTime#MAX} and 00:00-02:00.
 */
public record Shift(LocalTime start, LocalTime end) {

    public Shift {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("shift must start before it ends: " + start + "-" + end);
        }
    }

    public boolean contains(LocalTime time) {
        return !time.isBefore(start) && time.isBefore(end);
    }
}
