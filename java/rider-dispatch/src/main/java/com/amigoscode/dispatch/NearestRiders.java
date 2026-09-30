package com.amigoscode.dispatch;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/** Story 1: the K closest available riders to a restaurant, closest first. */
public final class NearestRiders {

    private NearestRiders() {
    }

    public static List<Rider> find(Collection<Rider> riders, Location restaurant, int k,
                                   Predicate<Rider> isAvailable) {
        throw new UnsupportedOperationException("story 1");
    }
}
