package com.amigoscode.dispatch;

import java.time.LocalTime;

/** A shift within one day. Decide (and test) whether the end time is inclusive. */
public record Shift(LocalTime start, LocalTime end) {

    public boolean contains(LocalTime time) {
        throw new UnsupportedOperationException("story 2");
    }
}
