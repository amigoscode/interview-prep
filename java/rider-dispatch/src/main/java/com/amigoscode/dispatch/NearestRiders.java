package com.amigoscode.dispatch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.function.Predicate;

/** Story 1: the K closest available riders to a restaurant, closest first. */
public final class NearestRiders {

    private NearestRiders() {
    }

    private record Candidate(Rider rider, double distanceKm) {
    }

    /** Closest first; equal distances ordered by rider id so the answer is deterministic. */
    private static final Comparator<Candidate> CLOSEST_FIRST = Comparator
            .comparingDouble(Candidate::distanceKm)
            .thenComparing(c -> c.rider().id());

    /**
     * Returns up to {@code k} riders accepted by {@code isAvailable}, closest to {@code restaurant}
     * first. Keeps a max-heap of the best {@code k} seen so far: O(n log k) time, O(k) space.
     */
    public static List<Rider> find(Collection<Rider> riders, Location restaurant, int k,
                                   Predicate<Rider> isAvailable) {
        Objects.requireNonNull(riders, "riders");
        Objects.requireNonNull(restaurant, "restaurant");
        Objects.requireNonNull(isAvailable, "isAvailable");
        if (k < 0) {
            throw new IllegalArgumentException("k must not be negative: " + k);
        }
        if (k == 0) {
            return List.of();
        }

        // Head of the heap is the WORST of the current best k, so it is the one to evict.
        PriorityQueue<Candidate> worstFirst = new PriorityQueue<>(CLOSEST_FIRST.reversed());
        for (Rider rider : riders) {
            if (!isAvailable.test(rider)) {
                continue;
            }
            Candidate candidate = new Candidate(rider, restaurant.distanceKmTo(rider.location()));
            if (worstFirst.size() < k) {
                worstFirst.offer(candidate);
            } else if (CLOSEST_FIRST.compare(candidate, worstFirst.peek()) < 0) {
                worstFirst.poll();
                worstFirst.offer(candidate);
            }
        }

        List<Candidate> best = new ArrayList<>(worstFirst);
        best.sort(CLOSEST_FIRST);
        return best.stream().map(Candidate::rider).toList();
    }
}
