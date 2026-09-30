package com.amigoscode.arrays;

import java.util.Objects;

/** An order placed today, and the delivery zone it belongs to. */
public record Order(String id, String zone) {

    public Order {
        Objects.requireNonNull(id, "id");
        // groupingBy throws on a null key anyway; failing here says which order is broken.
        Objects.requireNonNull(zone, "zone");
    }
}
