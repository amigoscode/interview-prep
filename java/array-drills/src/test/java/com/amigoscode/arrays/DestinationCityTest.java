package com.amigoscode.arrays;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Every behaviour is checked against both the loop and the stream version. */
class DestinationCityTest {

    static Stream<Named<Function<List<Leg>, String>>> implementations() {
        return Stream.of(
                Named.of("loops", DestinationCity::finalDestination),
                Named.of("streams", DestinationCity::finalDestinationWithStreams));
    }

    private static Leg leg(String from, String to) {
        return new Leg(from, to);
    }

    @Nested
    class ValidPaths {

        @ParameterizedTest
        @MethodSource("com.amigoscode.arrays.DestinationCityTest#implementations")
        void leetCodeExample(Function<List<Leg>, String> destination) {
            List<Leg> legs = List.of(
                    leg("London", "New York"), leg("New York", "Lima"), leg("Lima", "Sao Paulo"));
            assertThat(destination.apply(legs)).isEqualTo("Sao Paulo");
        }

        @ParameterizedTest
        @MethodSource("com.amigoscode.arrays.DestinationCityTest#implementations")
        void legsMayArriveInAnyOrder(Function<List<Leg>, String> destination) {
            List<Leg> legs = List.of(
                    leg("Hub", "Kitchen"), leg("Depot", "Hub"), leg("Kitchen", "Customer"), leg("Garage", "Depot"));
            assertThat(destination.apply(legs)).isEqualTo("Customer");
        }

        @ParameterizedTest
        @MethodSource("com.amigoscode.arrays.DestinationCityTest#implementations")
        void singleLegEndsAtItsTo(Function<List<Leg>, String> destination) {
            assertThat(destination.apply(List.of(leg("Kitchen", "Customer")))).isEqualTo("Customer");
        }

        @ParameterizedTest
        @MethodSource("com.amigoscode.arrays.DestinationCityTest#implementations")
        void cityNamesMatchExactlyAndAreCaseSensitive(Function<List<Leg>, String> destination) {
            // "berlin" is a different city from "Berlin": the path is Berlin -> Paris -> berlin.
            List<Leg> legs = List.of(leg("Berlin", "Paris"), leg("Paris", "berlin"));
            assertThat(destination.apply(legs)).isEqualTo("berlin");
        }

        @ParameterizedTest
        @MethodSource("com.amigoscode.arrays.DestinationCityTest#implementations")
        void longShuffledPath(Function<List<Leg>, String> destination) {
            List<Leg> legs = new ArrayList<>(IntStream.range(0, 100_000)
                    .mapToObj(i -> leg("c" + i, "c" + (i + 1)))
                    .toList());
            Collections.shuffle(legs, new Random(42));
            assertThat(destination.apply(legs)).isEqualTo("c100000");
        }
    }

    @Nested
    class InvalidInput {

        @ParameterizedTest
        @MethodSource("com.amigoscode.arrays.DestinationCityTest#implementations")
        void emptyListHasNoDestination(Function<List<Leg>, String> destination) {
            assertThatThrownBy(() -> destination.apply(List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @MethodSource("com.amigoscode.arrays.DestinationCityTest#implementations")
        void loopHasNoDeadEnd(Function<List<Leg>, String> destination) {
            assertThatThrownBy(() -> destination.apply(List.of(leg("A", "B"), leg("B", "C"), leg("C", "A"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("loop");
            assertThatThrownBy(() -> destination.apply(List.of(leg("A", "A"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("loop");
        }

        @ParameterizedTest
        @MethodSource("com.amigoscode.arrays.DestinationCityTest#implementations")
        void forkHasTwoDeadEnds(Function<List<Leg>, String> destination) {
            assertThatThrownBy(() -> destination.apply(List.of(leg("A", "B"), leg("A", "C"))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("B")
                    .hasMessageContaining("C");
        }

        @ParameterizedTest
        @MethodSource("com.amigoscode.arrays.DestinationCityTest#implementations")
        void disjointPathsHaveTwoDeadEnds(Function<List<Leg>, String> destination) {
            assertThatThrownBy(() -> destination.apply(List.of(leg("A", "B"), leg("C", "D"))))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @MethodSource("com.amigoscode.arrays.DestinationCityTest#implementations")
        void aRepeatedLegStillHasOneDeadEnd(Function<List<Leg>, String> destination) {
            // The same city reached twice is counted once, not reported as two dead ends.
            assertThat(destination.apply(List.of(leg("A", "B"), leg("A", "B")))).isEqualTo("B");
        }

        @ParameterizedTest
        @MethodSource("com.amigoscode.arrays.DestinationCityTest#implementations")
        void documentedLimitAPathPlusASeparateLoopIsNotDetected(Function<List<Leg>, String> destination) {
            // Exactly one dead end, so the O(n) check accepts it. See the class javadoc.
            List<Leg> legs = List.of(leg("A", "B"), leg("C", "D"), leg("D", "C"));
            assertThat(destination.apply(legs)).isEqualTo("B");
        }

        @ParameterizedTest
        @MethodSource("com.amigoscode.arrays.DestinationCityTest#implementations")
        void rejectsNullListAndNullLeg(Function<List<Leg>, String> destination) {
            assertThatThrownBy(() -> destination.apply(null)).isInstanceOf(NullPointerException.class);
            List<Leg> withNull = Arrays.asList(leg("A", "B"), null);
            assertThatThrownBy(() -> destination.apply(withNull)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void aLegRejectsNullCities() {
            assertThatThrownBy(() -> leg(null, "B")).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> leg("A", null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Test
    void bothVersionsAgreeOnRandomPaths() {
        Random random = new Random(7);
        for (int run = 0; run < 200; run++) {
            int n = 1 + random.nextInt(30);
            List<String> cities = IntStream.rangeClosed(0, n)
                    .mapToObj(i -> "zone-" + i)
                    .collect(Collectors.toCollection(ArrayList::new));
            Collections.shuffle(cities, random);
            List<Leg> legs = new ArrayList<>(IntStream.range(0, n)
                    .mapToObj(i -> leg(cities.get(i), cities.get(i + 1)))
                    .toList());
            Collections.shuffle(legs, random);

            assertThat(DestinationCity.finalDestination(legs))
                    .isEqualTo(DestinationCity.finalDestinationWithStreams(legs))
                    .isEqualTo(cities.getLast());
        }
    }
}
