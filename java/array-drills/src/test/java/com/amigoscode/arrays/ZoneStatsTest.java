package com.amigoscode.arrays;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static com.amigoscode.arrays.ZoneStats.busiestZone;
import static com.amigoscode.arrays.ZoneStats.ordersPerZone;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

class ZoneStatsTest {

    private static List<Order> orders(String... zones) {
        return IntStream.range(0, zones.length).mapToObj(i -> new Order("o" + i, zones[i])).toList();
    }

    @Nested
    class OrdersPerZone {

        @Test
        void countsOrdersPerZoneInZoneNameOrder() {
            assertThat(ordersPerZone(orders("Mitte", "Kreuzberg", "Mitte", "Wedding", "Mitte", "Kreuzberg")))
                    .containsExactly(entry("Kreuzberg", 2L), entry("Mitte", 3L), entry("Wedding", 1L));
        }

        @Test
        void noOrdersGiveAnEmptyMap() {
            assertThat(ordersPerZone(List.of())).isEmpty();
        }

        @Test
        void rejectsNull() {
            assertThatThrownBy(() -> ordersPerZone(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class BusiestZone {

        @Test
        void returnsTheZoneWithTheMostOrders() {
            assertThat(busiestZone(orders("Mitte", "Kreuzberg", "Mitte", "Wedding", "Mitte", "Kreuzberg")))
                    .contains("Mitte");
        }

        @Test
        void singleOrder() {
            assertThat(busiestZone(orders("Wedding"))).contains("Wedding");
        }

        @Test
        void noOrdersMeansNoBusiestZone() {
            assertThat(busiestZone(List.of())).isEmpty();
        }

        @Test
        void tiesGoToTheAlphabeticallyFirstZone() {
            assertThat(busiestZone(orders("Wedding", "Kreuzberg", "Mitte", "Wedding", "Kreuzberg", "Mitte")))
                    .contains("Kreuzberg");
            assertThat(busiestZone(orders("Zehlendorf", "Adlershof"))).contains("Adlershof");
        }

        @Test
        void tieBreakDoesNotDependOnInputOrderOrHashing() {
            // Many zones all with one order: HashMap iteration order is arbitrary, the answer is not.
            List<String> zones = IntStream.range(0, 1_000).mapToObj(i -> "zone-" + (999 - i)).toList();
            assertThat(busiestZone(orders(zones.toArray(String[]::new)))).contains("zone-0");
        }

        @Test
        void aClearWinnerBeatsAnEarlierName() {
            assertThat(busiestZone(orders("Adlershof", "Zehlendorf", "Zehlendorf"))).contains("Zehlendorf");
        }

        @Test
        void zoneNamesAreCaseSensitive() {
            assertThat(busiestZone(orders("mitte", "Mitte", "mitte"))).contains("mitte");
        }

        @Test
        void rejectsNullListAndNullZone() {
            assertThatThrownBy(() -> busiestZone(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new Order("o1", null)).isInstanceOf(NullPointerException.class);
        }
    }
}
