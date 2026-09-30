package com.amigoscode.orders;

import com.amigoscode.orders.ApplyResult.Applied;
import com.amigoscode.orders.ApplyResult.Duplicate;
import com.amigoscode.orders.ApplyResult.Rejected;
import com.amigoscode.orders.ApplyResult.Stale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.amigoscode.orders.OrderState.ACCEPTED;
import static com.amigoscode.orders.OrderState.CANCELLED;
import static com.amigoscode.orders.OrderState.DELIVERED;
import static com.amigoscode.orders.OrderState.PICKED_UP;
import static com.amigoscode.orders.OrderState.RECEIVED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTrackerTest {

    static final Instant T0 = Instant.parse("2026-09-19T18:00:00Z");
    static final Duration PROMISE = Duration.ofMinutes(30);

    MutableClock clock;
    OrderTracker tracker;
    AtomicInteger ids = new AtomicInteger();

    @BeforeEach
    void setUp() {
        clock = new MutableClock(T0);
        tracker = new OrderTracker(clock);
    }

    /** An event for an order placed at T0 at restaurant r1 in zone berlin-mitte. */
    OrderEvent event(String orderId, long sequence, OrderState state) {
        return event(orderId, sequence, state, "r1", "berlin-mitte", T0);
    }

    OrderEvent event(String orderId, long sequence, OrderState state, String restaurant, String zone, Instant placedAt) {
        return new OrderEvent("e" + ids.incrementAndGet(), orderId, sequence, state, clock.instant(),
                new OrderDetails(restaurant, zone, placedAt, placedAt.plus(PROMISE)));
    }

    @Nested
    class Story1StateMachine {

        @Test
        void happyPathMovesThroughEveryState() {
            assertThat(tracker.apply(event("o1", 1, RECEIVED))).isEqualTo(new Applied(RECEIVED));
            assertThat(tracker.apply(event("o1", 2, ACCEPTED))).isEqualTo(new Applied(ACCEPTED));
            assertThat(tracker.apply(event("o1", 3, PICKED_UP))).isEqualTo(new Applied(PICKED_UP));
            assertThat(tracker.apply(event("o1", 4, DELIVERED))).isEqualTo(new Applied(DELIVERED));
            assertThat(tracker.stateOf("o1")).contains(DELIVERED);
        }

        @Test
        void unknownOrderHasNoState() {
            assertThat(tracker.stateOf("nope")).isEmpty();
        }

        @Test
        void cancelFromReceivedOrAccepted() {
            tracker.apply(event("o1", 1, RECEIVED));
            assertThat(tracker.apply(event("o1", 2, CANCELLED))).isEqualTo(new Applied(CANCELLED));

            tracker.apply(event("o2", 1, RECEIVED));
            tracker.apply(event("o2", 2, ACCEPTED));
            assertThat(tracker.apply(event("o2", 3, CANCELLED))).isEqualTo(new Applied(CANCELLED));
        }

        @Test
        void cannotCancelOnceTheRiderHasTheFood() {
            tracker.apply(event("o1", 1, RECEIVED));
            tracker.apply(event("o1", 2, ACCEPTED));
            tracker.apply(event("o1", 3, PICKED_UP));
            assertThat(tracker.apply(event("o1", 4, CANCELLED))).isEqualTo(new Rejected(PICKED_UP, CANCELLED));
            assertThat(tracker.stateOf("o1")).contains(PICKED_UP);
        }

        @ParameterizedTest(name = "{0} -> {1} is rejected")
        @CsvSource({
                "DELIVERED, CANCELLED", "DELIVERED, RECEIVED", "CANCELLED, ACCEPTED",
                "CANCELLED, DELIVERED", "ACCEPTED, RECEIVED", "PICKED_UP, ACCEPTED", "ACCEPTED, ACCEPTED"})
        void illegalTransitionsAreRejectedAndChangeNothing(OrderState from, OrderState to) {
            tracker.apply(event("o1", 1, from));
            assertThat(tracker.apply(event("o1", 2, to))).isEqualTo(new Rejected(from, to));
            assertThat(tracker.stateOf("o1")).contains(from);
        }

        @Test
        void rejectedEventDoesNotConsumeTheSequence() {
            tracker.apply(event("o1", 1, RECEIVED));
            tracker.apply(event("o1", 2, RECEIVED));                   // rejected
            assertThat(tracker.apply(event("o1", 2, ACCEPTED))).isEqualTo(new Applied(ACCEPTED));
        }

        @Test
        void ordersAreIndependent() {
            tracker.apply(event("o1", 1, RECEIVED));
            tracker.apply(event("o2", 1, RECEIVED));
            tracker.apply(event("o2", 2, CANCELLED));
            assertThat(tracker.stateOf("o1")).contains(RECEIVED);
            assertThat(tracker.stateOf("o2")).contains(CANCELLED);
        }

        @Test
        void malformedInputIsAProgrammingErrorNotAResult() {
            OrderDetails details = new OrderDetails("r1", "z1", T0, T0.plus(PROMISE));
            assertThatThrownBy(() -> tracker.apply(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new OrderEvent(" ", "o1", 1, RECEIVED, T0, details))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new OrderEvent("e1", "", 1, RECEIVED, T0, details))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new OrderEvent("e1", "o1", 0, RECEIVED, T0, details))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new OrderEvent("e1", "o1", 1, null, T0, details))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new OrderDetails("r1", "z1", T0, T0.minusSeconds(1)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new OrderTracker(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new OrderTracker(clock, 0)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Story2DuplicatesAndOrdering {

        @Test
        void redeliveredEventIsIgnored() {
            OrderEvent accepted = event("o1", 2, ACCEPTED);
            tracker.apply(event("o1", 1, RECEIVED));
            assertThat(tracker.apply(accepted)).isEqualTo(new Applied(ACCEPTED));
            assertThat(tracker.apply(accepted)).isEqualTo(new Duplicate());
            assertThat(tracker.apply(accepted)).isEqualTo(new Duplicate());
            assertThat(tracker.stateOf("o1")).contains(ACCEPTED);
        }

        @Test
        void duplicateIsReportedAsDuplicateEvenAfterNewerEvents() {
            OrderEvent received = event("o1", 1, RECEIVED);
            tracker.apply(received);
            tracker.apply(event("o1", 2, ACCEPTED));
            assertThat(tracker.apply(received)).isEqualTo(new Duplicate());
        }

        @Test
        void redeliveredRejectedEventIsADuplicateToo() {
            tracker.apply(event("o1", 1, RECEIVED));
            tracker.apply(event("o1", 2, DELIVERED));
            OrderEvent illegal = event("o1", 3, ACCEPTED);
            assertThat(tracker.apply(illegal)).isEqualTo(new Rejected(DELIVERED, ACCEPTED));
            assertThat(tracker.apply(illegal)).isEqualTo(new Duplicate());
        }

        @Test
        void olderSequenceIsIgnoredAsStale() {
            OrderEvent lateAccepted = event("o1", 2, ACCEPTED);
            tracker.apply(event("o1", 1, RECEIVED));
            tracker.apply(event("o1", 3, PICKED_UP));
            assertThat(tracker.apply(lateAccepted)).isEqualTo(new Stale(3));
            assertThat(tracker.stateOf("o1")).contains(PICKED_UP);
        }

        @Test
        void sameSequenceWithADifferentEventIdIsStale() {
            tracker.apply(event("o1", 1, RECEIVED));
            tracker.apply(event("o1", 2, ACCEPTED));
            assertThat(tracker.apply(event("o1", 2, CANCELLED))).isEqualTo(new Stale(2));
            assertThat(tracker.stateOf("o1")).contains(ACCEPTED);
        }

        @Test
        void gapIsAcceptedWhenTheNewStateIsReachable() {
            tracker.apply(event("o1", 1, RECEIVED));
            // ACCEPTED (seq 2) is still in flight
            assertThat(tracker.apply(event("o1", 3, PICKED_UP))).isEqualTo(new Applied(PICKED_UP));
            assertThat(tracker.apply(event("o1", 2, ACCEPTED))).isEqualTo(new Stale(3));
            assertThat(tracker.stateOf("o1")).contains(PICKED_UP);
        }

        @Test
        void gapIsRejectedWhenTheNewStateIsNotReachable() {
            tracker.apply(event("o1", 1, RECEIVED));
            tracker.apply(event("o1", 2, ACCEPTED));
            tracker.apply(event("o1", 3, PICKED_UP));
            assertThat(tracker.apply(event("o1", 5, CANCELLED))).isEqualTo(new Rejected(PICKED_UP, CANCELLED));
        }

        @Test
        void firstEventSeenNeedNotBeReceived() {
            // Out of order across the whole order: DELIVERED arrives before anything else.
            assertThat(tracker.apply(event("o1", 4, DELIVERED))).isEqualTo(new Applied(DELIVERED));
            assertThat(tracker.apply(event("o1", 1, RECEIVED))).isEqualTo(new Stale(4));
            assertThat(tracker.stateOf("o1")).contains(DELIVERED);
        }

        @Test
        void fullyShuffledStreamWithDuplicatesEndsInTheLatestState() {
            OrderEvent r = event("o1", 1, RECEIVED), a = event("o1", 2, ACCEPTED),
                    p = event("o1", 3, PICKED_UP), d = event("o1", 4, DELIVERED);
            for (OrderEvent e : new OrderEvent[]{a, r, a, d, p, r, d}) tracker.apply(e);
            assertThat(tracker.stateOf("o1")).contains(DELIVERED);
        }
    }

    @Nested
    class Story3LateOrders {

        @Test
        void countsOpenOrdersPastTheirPromisePerZone() {
            tracker.apply(event("o1", 1, RECEIVED, "r1", "mitte", T0));
            tracker.apply(event("o2", 1, RECEIVED, "r1", "mitte", T0));
            tracker.apply(event("o3", 1, RECEIVED, "r2", "kreuzberg", T0));
            tracker.apply(event("o4", 1, RECEIVED, "r2", "kreuzberg", T0.plus(Duration.ofMinutes(20))));
            assertThat(tracker.lateOrdersPerZone()).isEmpty();

            clock.advance(Duration.ofMinutes(31));
            assertThat(tracker.lateOrdersPerZone()).isEqualTo(Map.of("mitte", 2L, "kreuzberg", 1L));

            clock.advance(Duration.ofMinutes(20));
            assertThat(tracker.lateOrdersPerZone()).isEqualTo(Map.of("mitte", 2L, "kreuzberg", 2L));
        }

        @Test
        void exactlyAtThePromisedTimeIsNotLate() {
            tracker.apply(event("o1", 1, RECEIVED));
            clock.advance(PROMISE);
            assertThat(tracker.lateOrdersPerZone()).isEmpty();
            clock.advance(Duration.ofMillis(1));
            assertThat(tracker.lateOrdersPerZone()).containsEntry("berlin-mitte", 1L);
        }

        @Test
        void deliveredAndCancelledOrdersAreNeverLate() {
            tracker.apply(event("o1", 1, RECEIVED));
            tracker.apply(event("o2", 1, RECEIVED));
            tracker.apply(event("o3", 1, RECEIVED));
            clock.advance(Duration.ofMinutes(40));
            tracker.apply(event("o1", 4, DELIVERED));
            tracker.apply(event("o2", 2, CANCELLED));
            assertThat(tracker.lateOrdersPerZone()).isEqualTo(Map.of("berlin-mitte", 1L));
        }

        @Test
        void countsLateDeliveriesInASlidingWindow() {
            tracker.apply(event("onTime", 1, RECEIVED));
            tracker.apply(event("late1", 1, RECEIVED));
            tracker.apply(event("late2", 1, RECEIVED));

            clock.advance(Duration.ofMinutes(25));
            tracker.apply(event("onTime", 4, DELIVERED));        // 25 min: on time
            clock.advance(Duration.ofMinutes(10));
            tracker.apply(event("late1", 4, DELIVERED));         // 35 min: late
            clock.advance(Duration.ofMinutes(10));
            tracker.apply(event("late2", 4, DELIVERED));         // 45 min: late

            assertThat(tracker.lateDeliveriesInLast(Duration.ofMinutes(15))).isEqualTo(2);
            assertThat(tracker.lateDeliveriesInLast(Duration.ofMinutes(5))).isEqualTo(1);

            clock.advance(Duration.ofMinutes(10));               // now T0+55: late1 at 35 is 20 min ago
            assertThat(tracker.lateDeliveriesInLast(Duration.ofMinutes(20))).isEqualTo(1); // boundary excluded
            assertThat(tracker.lateDeliveriesInLast(Duration.ofMinutes(21))).isEqualTo(2);
            clock.advance(Duration.ofHours(1));
            assertThat(tracker.lateDeliveriesInLast(Duration.ofMinutes(30))).isZero();
        }

        @Test
        void windowUsesTheEventTimeOfTheDelivery() {
            tracker.apply(event("o1", 1, RECEIVED));
            clock.advance(Duration.ofMinutes(40));
            tracker.apply(event("o1", 4, DELIVERED));            // delivered at T0+40, late
            clock.advance(Duration.ofMinutes(30));               // the consumer is 30 min behind
            assertThat(tracker.lateDeliveriesInLast(Duration.ofMinutes(10))).isZero();
            assertThat(tracker.lateDeliveriesInLast(Duration.ofMinutes(31))).isEqualTo(1);
        }

        @Test
        void rejectsAnEmptyWindow() {
            assertThatThrownBy(() -> tracker.lateDeliveriesInLast(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> tracker.lateDeliveriesInLast(Duration.ofMinutes(-1))).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> tracker.lateDeliveriesInLast(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class Story4TopRestaurantsAndAverage {

        void place(String orderId, String restaurant) {
            tracker.apply(event(orderId, 1, RECEIVED, restaurant, "z1", clock.instant()));
        }

        @Test
        void topKByOrdersInTheLastHourBusiestFirst() {
            place("o1", "pizza"); place("o2", "pizza"); place("o3", "pizza");
            place("o4", "sushi"); place("o5", "sushi");
            place("o6", "doner");
            assertThat(tracker.topRestaurants(2)).containsExactly(
                    new RestaurantCount("pizza", 3), new RestaurantCount("sushi", 2));
            assertThat(tracker.topRestaurants(10)).hasSize(3);
        }

        @Test
        void tiesAreBrokenByRestaurantIdSoTheAnswerIsStable() {
            place("o1", "b"); place("o2", "a"); place("o3", "c");
            assertThat(tracker.topRestaurants(2)).containsExactly(
                    new RestaurantCount("a", 1), new RestaurantCount("b", 1));
        }

        @Test
        void ordersOlderThanAnHourAreEvicted() {
            place("o1", "pizza"); place("o2", "pizza");
            clock.advance(Duration.ofMinutes(30));
            place("o3", "sushi");
            clock.advance(Duration.ofMinutes(30));               // pizza orders are exactly 1h old
            assertThat(tracker.topRestaurants(5)).containsExactly(new RestaurantCount("sushi", 1));
            clock.advance(Duration.ofMinutes(31));
            assertThat(tracker.topRestaurants(5)).isEmpty();
        }

        @Test
        void countsEachOrderOnceHoweverManyEventsItHas() {
            place("o1", "pizza");
            tracker.apply(event("o1", 2, ACCEPTED, "pizza", "z1", T0));
            tracker.apply(event("o1", 3, PICKED_UP, "pizza", "z1", T0));
            assertThat(tracker.topRestaurants(1)).containsExactly(new RestaurantCount("pizza", 1));
        }

        @Test
        void countsByPlacedAtEvenWhenTheEventArrivesLate() {
            clock.advance(Duration.ofMinutes(90));
            tracker.apply(event("old", 1, RECEIVED, "pizza", "z1", T0));                          // placed 90 min ago
            tracker.apply(event("recent", 1, RECEIVED, "sushi", "z1", T0.plus(Duration.ofMinutes(60))));
            assertThat(tracker.topRestaurants(5)).containsExactly(new RestaurantCount("sushi", 1));
        }

        @Test
        void rejectsNonPositiveK() {
            assertThatThrownBy(() -> tracker.topRestaurants(0)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void noDeliveriesMeansNoAverage() {
            assertThat(tracker.averageDeliveryTime()).isEmpty();
        }

        @Test
        void movingAverageOverTheLastNDeliveries() {
            tracker = new OrderTracker(clock, 3);
            deliverAfter("o1", Duration.ofMinutes(10));
            assertThat(tracker.averageDeliveryTime()).contains(Duration.ofMinutes(10));
            deliverAfter("o2", Duration.ofMinutes(20));
            deliverAfter("o3", Duration.ofMinutes(30));
            assertThat(tracker.averageDeliveryTime()).contains(Duration.ofMinutes(20));
            deliverAfter("o4", Duration.ofMinutes(40));         // o1 drops out: (20+30+40)/3
            assertThat(tracker.averageDeliveryTime()).contains(Duration.ofMinutes(30));
        }

        @Test
        void cancelledAndDuplicateDeliveriesDoNotSkewTheAverage() {
            deliverAfter("o1", Duration.ofMinutes(20));
            OrderEvent delivered = event("o2", 4, DELIVERED, "r1", "z1", clock.instant().minus(Duration.ofMinutes(40)));
            tracker.apply(delivered);
            tracker.apply(delivered);                            // redelivery
            place("o3", "r1");
            tracker.apply(event("o3", 2, CANCELLED, "r1", "z1", clock.instant()));
            assertThat(tracker.averageDeliveryTime()).contains(Duration.ofMinutes(30));
        }

        void deliverAfter(String orderId, Duration took) {
            Instant placedAt = clock.instant();
            tracker.apply(event(orderId, 1, RECEIVED, "r1", "z1", placedAt));
            clock.advance(took);
            tracker.apply(event(orderId, 4, DELIVERED, "r1", "z1", placedAt));
        }
    }
}
