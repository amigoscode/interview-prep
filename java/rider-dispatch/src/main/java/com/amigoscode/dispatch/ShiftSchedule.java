package com.amigoscode.dispatch;

import java.time.LocalTime;
import java.util.List;

/** Story 2: each rider's shifts for the day, merged, and "is this rider on shift at this time?". */
public final class ShiftSchedule {

    public void addShift(String riderId, LocalTime start, LocalTime end) {
        throw new UnsupportedOperationException("story 2");
    }

    /** The rider's shifts, merged and sorted by start. */
    public List<Shift> shiftsOf(String riderId) {
        throw new UnsupportedOperationException("story 2");
    }

    public boolean isOnShift(String riderId, LocalTime time) {
        throw new UnsupportedOperationException("story 2");
    }

    /** Merge overlapping and touching shifts. */
    public static List<Shift> merge(List<Shift> shifts) {
        throw new UnsupportedOperationException("story 2");
    }
}
