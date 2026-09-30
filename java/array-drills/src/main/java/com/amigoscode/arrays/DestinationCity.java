package com.amigoscode.arrays;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.function.Predicate.not;

/**
 * Destination City (LeetCode 1436), delivery-flavoured: a rider's legs {@code [from, to]}, in any
 * order, form one path with no loops. The final destination is the only city that never appears
 * as a {@code from}.
 *
 * <p>Spec decisions (LeetCode guarantees valid input; a service cannot):
 * <ul>
 *   <li>null list, null leg or null city: {@link NullPointerException}</li>
 *   <li>empty list: {@link IllegalArgumentException}, there is no path and so no destination</li>
 *   <li>not exactly one city without an outgoing leg: {@link IllegalArgumentException}. Zero means
 *       every city leads somewhere, i.e. the legs contain a loop ({@code A->B, B->A}). Two or more
 *       means a fork ({@code A->B, A->C}) or several disjoint paths ({@code A->B, C->D})</li>
 * </ul>
 * This check is O(n) and catches most bad input, but it is not a full path validation: a valid
 * path plus a separate loop ({@code A->B, C->D, D->C}) still has exactly one dead end and is
 * accepted. Proving "one simple path" needs every city to have at most one leg in and one out and
 * a walk from the start that covers all n legs; still O(n), but out of scope here.
 */
public final class DestinationCity {

    private DestinationCity() {
    }

    /** O(n) time, O(n) space: one pass to collect the origins, one to find the {@code to} that is not one. */
    public static String finalDestination(List<Leg> legs) {
        requireLegs(legs);
        Set<String> origins = new HashSet<>();
        for (Leg leg : legs) {
            origins.add(leg.from());
        }
        Set<String> deadEnds = new LinkedHashSet<>();
        for (Leg leg : legs) {
            if (!origins.contains(leg.to())) {
                deadEnds.add(leg.to());
            }
        }
        return onlyOne(new ArrayList<>(deadEnds));
    }

    /** The same algorithm and the same contract, as a stream pipeline. */
    public static String finalDestinationWithStreams(List<Leg> legs) {
        requireLegs(legs);
        Set<String> origins = legs.stream().map(Leg::from).collect(Collectors.toSet());
        List<String> deadEnds = legs.stream()
                .map(Leg::to)
                .filter(not(origins::contains))
                .distinct()
                .toList();
        return onlyOne(deadEnds);
    }

    private static void requireLegs(List<Leg> legs) {
        Objects.requireNonNull(legs, "legs");
        // List.contains(null) throws on List.of(...), so check element by element.
        legs.forEach(leg -> Objects.requireNonNull(leg, "leg"));
        if (legs.isEmpty()) {
            throw new IllegalArgumentException("no legs, so no destination");
        }
    }

    private static String onlyOne(List<String> deadEnds) {
        if (deadEnds.isEmpty()) {
            throw new IllegalArgumentException("every city has an outgoing leg: the legs contain a loop");
        }
        if (deadEnds.size() > 1) {
            throw new IllegalArgumentException("legs do not form one path, several dead ends: " + deadEnds);
        }
        return deadEnds.getFirst();
    }
}
