package com.amigoscode.dispatch;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** Story 1. */
class NearestRidersTest {

    static final Location RESTAURANT = new Location(52.5200, 13.4050); // Berlin Mitte
    static final Predicate<Rider> EVERYONE = rider -> true;

    static Rider rider(String id, double lat, double lon) {
        return new Rider(id, new Location(lat, lon));
    }

    static List<String> ids(List<Rider> riders) {
        return riders.stream().map(Rider::id).toList();
    }

    @Nested
    class Distance {

        @Test
        void samePointIsZeroKilometres() {
            assertThat(RESTAURANT.distanceKmTo(RESTAURANT)).isEqualTo(0.0);
        }

        @Test
        void berlinToMunichIsAbout504Kilometres() {
            Location munich = new Location(48.1351, 11.5820);
            assertThat(RESTAURANT.distanceKmTo(munich)).isCloseTo(504, within(2.0));
            assertThat(munich.distanceKmTo(RESTAURANT)).isCloseTo(RESTAURANT.distanceKmTo(munich), within(1e-9));
        }

        @Test
        void oneDegreeOfLatitudeIsAbout111Kilometres() {
            assertThat(new Location(0, 0).distanceKmTo(new Location(1, 0))).isCloseTo(111.19, within(0.01));
        }

        @Test
        void pointsEitherSideOfTheAntimeridianAreClose() {
            Location west = new Location(0, 179.9);
            Location east = new Location(0, -179.9);
            assertThat(west.distanceKmTo(east)).isCloseTo(22.24, within(0.01)); // not ~40,000 km
        }

        @Test
        void rejectsCoordinatesOffTheGlobe() {
            assertThatThrownBy(() -> new Location(90.1, 0)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Location(-90.1, 0)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Location(0, 180.1)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Location(0, -180.1)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Location(Double.NaN, 0)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class FindNearest {

        @Test
        void returnsTheKClosestClosestFirst() {
            List<Rider> riders = List.of(
                    rider("far", 52.60, 13.40),
                    rider("near", 52.521, 13.405),
                    rider("mid", 52.53, 13.41),
                    rider("farthest", 53.00, 13.40));

            assertThat(ids(NearestRiders.find(riders, RESTAURANT, 3, EVERYONE)))
                    .containsExactly("near", "mid", "far");
        }

        @Test
        void skipsUnavailableRiders() {
            List<Rider> riders = List.of(
                    rider("near", 52.521, 13.405),
                    rider("mid", 52.53, 13.41),
                    rider("far", 52.60, 13.40));
            Set<String> busy = Set.of("near");

            assertThat(ids(NearestRiders.find(riders, RESTAURANT, 2, r -> !busy.contains(r.id()))))
                    .containsExactly("mid", "far");
        }

        @Test
        void equalDistancesAreOrderedByRiderId() {
            List<Rider> riders = List.of(
                    rider("c", 52.53, 13.405),
                    rider("b", 52.53, 13.405),
                    rider("a", 52.53, 13.405),
                    rider("d", 52.53, 13.405));

            assertThat(ids(NearestRiders.find(riders, RESTAURANT, 3, EVERYONE))).containsExactly("a", "b", "c");
        }

        @Test
        void fewerAvailableThanKReturnsAllOfThem() {
            List<Rider> riders = List.of(rider("b", 52.53, 13.41), rider("a", 52.521, 13.405));
            assertThat(ids(NearestRiders.find(riders, RESTAURANT, 10, EVERYONE))).containsExactly("a", "b");
        }

        @Test
        void noRidersOrKZeroGivesAnEmptyList() {
            assertThat(NearestRiders.find(List.of(), RESTAURANT, 5, EVERYONE)).isEmpty();
            assertThat(NearestRiders.find(List.of(rider("a", 52.5, 13.4)), RESTAURANT, 0, EVERYONE)).isEmpty();
            assertThat(NearestRiders.find(List.of(rider("a", 52.5, 13.4)), RESTAURANT, 1, r -> false)).isEmpty();
        }

        @Test
        void rejectsBadInput() {
            List<Rider> riders = List.of(rider("a", 52.5, 13.4));
            assertThatThrownBy(() -> NearestRiders.find(riders, RESTAURANT, -1, EVERYONE))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> NearestRiders.find(riders, null, 1, EVERYONE))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new Rider(" ", RESTAURANT)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Rider("a", null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void agreesWithSortingEveryoneOnARandomCity() {
            Random random = new Random(42);
            List<Rider> riders = new ArrayList<>();
            for (int i = 0; i < 5_000; i++) {
                riders.add(rider("r" + i, 52.4 + random.nextDouble() * 0.25, 13.2 + random.nextDouble() * 0.4));
            }
            Predicate<Rider> evenIdsOnly = r -> Integer.parseInt(r.id().substring(1)) % 2 == 0;

            List<Rider> expected = riders.stream()
                    .filter(evenIdsOnly)
                    .sorted(Comparator.<Rider>comparingDouble(r -> RESTAURANT.distanceKmTo(r.location()))
                            .thenComparing(Rider::id))
                    .limit(25)
                    .toList();

            assertThat(NearestRiders.find(riders, RESTAURANT, 25, evenIdsOnly)).containsExactlyElementsOf(expected);
        }
    }
}
