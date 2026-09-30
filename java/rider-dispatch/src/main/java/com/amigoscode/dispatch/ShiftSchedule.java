package com.amigoscode.dispatch;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Story 2: each rider's shifts for the day, merged, and "is this rider on shift at this time?". */
public final class ShiftSchedule {

    /** Always stored merged and sorted, so lookups never re-merge. Values are immutable lists. */
    private final Map<String, List<Shift>> shiftsByRider = new ConcurrentHashMap<>();

    public void addShift(String riderId, LocalTime start, LocalTime end) {
        requireId(riderId);
        Shift shift = new Shift(start, end);
        shiftsByRider.merge(riderId, List.of(shift), (existing, added) -> {
            List<Shift> all = new ArrayList<>(existing);
            all.addAll(added);
            return merge(all);
        });
    }

    /** The rider's shifts, merged and sorted by start. Empty for an unknown rider. */
    public List<Shift> shiftsOf(String riderId) {
        requireId(riderId);
        return shiftsByRider.getOrDefault(riderId, List.of());
    }

    public boolean isOnShift(String riderId, LocalTime time) {
        Objects.requireNonNull(time, "time");
        for (Shift shift : shiftsOf(riderId)) {
            if (shift.contains(time)) {
                return true;
            }
            if (time.isBefore(shift.start())) {
                return false; // sorted and disjoint: nothing later can contain it
            }
        }
        return false;
    }

    /**
     * Classic merge intervals: sort by start, then fold each shift into the last one when it
     * overlaps or touches (10:00-12:00 + 12:00-14:00 = 10:00-14:00). O(n log n).
     */
    public static List<Shift> merge(List<Shift> shifts) {
        Objects.requireNonNull(shifts, "shifts");
        List<Shift> sorted = new ArrayList<>(shifts);
        sorted.sort(Comparator.comparing(Shift::start));

        List<Shift> merged = new ArrayList<>();
        for (Shift shift : sorted) {
            int last = merged.size() - 1;
            if (last >= 0 && !shift.start().isAfter(merged.get(last).end())) {
                Shift previous = merged.get(last);
                LocalTime end = shift.end().isAfter(previous.end()) ? shift.end() : previous.end();
                merged.set(last, new Shift(previous.start(), end));
            } else {
                merged.add(shift);
            }
        }
        return List.copyOf(merged);
    }

    private static void requireId(String riderId) {
        if (riderId == null || riderId.isBlank()) {
            throw new IllegalArgumentException("rider id must not be blank");
        }
    }
}
