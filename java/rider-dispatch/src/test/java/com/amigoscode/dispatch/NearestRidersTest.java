package com.amigoscode.dispatch;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NearestRidersTest {

    @Test
    void projectIsWiredUp() {
        assertThat(NearestRiders.class).isNotNull();
    }

    @Test
    @Disabled("story 1: remove this line to begin")
    void returnsTheKClosestClosestFirst() {
        Location restaurant = new Location(52.5200, 13.4050); // Berlin Mitte
        List<Rider> riders = List.of(
                new Rider("far", new Location(52.60, 13.40)),
                new Rider("near", new Location(52.521, 13.405)),
                new Rider("mid", new Location(52.53, 13.41)),
                new Rider("farthest", new Location(53.00, 13.40)));

        List<Rider> nearest = NearestRiders.find(riders, restaurant, 3, rider -> true);

        assertThat(nearest).extracting(Rider::id).containsExactly("near", "mid", "far");
    }
}
