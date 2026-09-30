package com.amigoscode.arrays;

import java.util.Arrays;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Order placement times in epoch millis, sorted ascending (equal times allowed). Both queries
 * are O(log n) and built on one helper, {@link #lowerBound}: the first index whose timestamp is
 * {@code >= t}.
 */
public final class OrderTimeline {

    private final long[] timestamps;

    public OrderTimeline(long[] sortedTimestamps) {
        Objects.requireNonNull(sortedTimestamps, "sortedTimestamps");
        for (int i = 1; i < sortedTimestamps.length; i++) {
            if (sortedTimestamps[i] < sortedTimestamps[i - 1]) {
                throw new IllegalArgumentException("timestamps must be sorted ascending; index " + i + " is out of order");
            }
        }
        this.timestamps = Arrays.copyOf(sortedTimestamps, sortedTimestamps.length);
    }

    /** The earliest order at or after {@code t}, or empty when every order is before it. */
    public OptionalLong firstAtOrAfter(long t) {
        int index = lowerBound(t);
        return index < timestamps.length ? OptionalLong.of(timestamps[index]) : OptionalLong.empty();
    }

    /** Orders with {@code from <= timestamp < to}. Half-open, so adjacent windows never double count. */
    public int countInWindow(long from, long to) {
        if (from > to) {
            throw new IllegalArgumentException("from must not be after to: [" + from + ", " + to + ")");
        }
        return lowerBound(to) - lowerBound(from);
    }

    /** First index with {@code timestamps[index] >= t}, or {@code length} if there is none. */
    private int lowerBound(long t) {
        int lo = 0;
        int hi = timestamps.length; // search range is [lo, hi)
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (timestamps[mid] < t) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }
}
