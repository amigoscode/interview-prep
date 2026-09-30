package com.amigoscode.dispatch;

import com.amigoscode.dispatch.AssignmentResult.Assigned;
import com.amigoscode.dispatch.AssignmentResult.NoRiderAvailable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Stories 2 and 3: availability means on shift and free; assign and complete. */
class DispatcherTest {

    static final Location RESTAURANT = new Location(52.5200, 13.4050);

    // Closest to farthest from the restaurant.
    static final Rider NEAR = new Rider("near", new Location(52.521, 13.405));
    static final Rider MID = new Rider("mid", new Location(52.530, 13.410));
    static final Rider FAR = new Rider("far", new Location(52.600, 13.400));

    MutableClock clock;
    ShiftSchedule schedule;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-09-30T12:00:00Z")); // 12:00 local (UTC)
        schedule = new ShiftSchedule();
    }

    void allDay(Rider... riders) {
        for (Rider rider : riders) {
            schedule.addShift(rider.id(), LocalTime.MIN, LocalTime.MAX);
        }
    }

    Dispatcher dispatcher(Rider... riders) {
        return new Dispatcher(List.of(riders), schedule, clock);
    }

    static Order order(String id) {
        return new Order(id, RESTAURANT);
    }

    static String riderIdOf(AssignmentResult result) {
        assertThat(result).isInstanceOf(Assigned.class);
        return ((Assigned) result).rider().id();
    }

    @Nested
    class Story2OnlyOnShiftRidersAreAvailable {

        @Test
        void offShiftRidersAreSkippedEvenWhenClosest() {
            schedule.addShift("near", LocalTime.of(18, 0), LocalTime.of(22, 0)); // evening only
            allDay(MID, FAR);
            Dispatcher dispatcher = dispatcher(NEAR, MID, FAR);

            assertThat(dispatcher.nearestAvailable(RESTAURANT, 3)).containsExactly(MID, FAR);
        }

        @Test
        void aRiderWithNoShiftsIsNeverAvailable() {
            allDay(FAR);
            assertThat(dispatcher(NEAR, FAR).nearestAvailable(RESTAURANT, 2)).containsExactly(FAR);
        }

        @Test
        void availabilityFollowsTheClock() {
            schedule.addShift("near", LocalTime.of(12, 0), LocalTime.of(13, 0));
            Dispatcher dispatcher = dispatcher(NEAR);

            assertThat(dispatcher.nearestAvailable(RESTAURANT, 1)).containsExactly(NEAR);
            clock.advance(Duration.ofHours(1)); // 13:00, shift end is exclusive
            assertThat(dispatcher.nearestAvailable(RESTAURANT, 1)).isEmpty();
        }
    }

    @Nested
    class Story3AssignAndComplete {

        @Test
        void assignsTheNearestAvailableRiderAndMarksThemBusy() {
            allDay(NEAR, MID, FAR);
            Dispatcher dispatcher = dispatcher(FAR, NEAR, MID);

            AssignmentResult result = dispatcher.assign(order("o1"));

            assertThat(result).isEqualTo(new Assigned(order("o1"), NEAR));
            assertThat(dispatcher.isBusy("near")).isTrue();
            assertThat(dispatcher.riderFor("o1")).contains(NEAR);
            assertThat(dispatcher.nearestAvailable(RESTAURANT, 3)).containsExactly(MID, FAR);
        }

        @Test
        void aBusyRiderIsNeverGivenASecondOrder() {
            allDay(NEAR, MID);
            Dispatcher dispatcher = dispatcher(NEAR, MID);

            assertThat(riderIdOf(dispatcher.assign(order("o1")))).isEqualTo("near");
            assertThat(riderIdOf(dispatcher.assign(order("o2")))).isEqualTo("mid");
            assertThat(dispatcher.assign(order("o3"))).isEqualTo(new NoRiderAvailable(order("o3")));
        }

        @Test
        void noRidersAtAllIsAnExplicitResult() {
            assertThat(dispatcher().assign(order("o1"))).isInstanceOf(NoRiderAvailable.class);
        }

        @Test
        void offShiftRidersAreNotAssigned() {
            schedule.addShift("near", LocalTime.of(6, 0), LocalTime.of(11, 0));
            Dispatcher dispatcher = dispatcher(NEAR);

            assertThat(dispatcher.assign(order("o1"))).isInstanceOf(NoRiderAvailable.class);
            assertThat(dispatcher.isBusy("near")).isFalse();
        }

        @Test
        void completingFreesTheRiderForTheNextOrder() {
            allDay(NEAR, FAR);
            Dispatcher dispatcher = dispatcher(NEAR, FAR);
            dispatcher.assign(order("o1"));

            assertThat(dispatcher.complete("o1")).isTrue();

            assertThat(dispatcher.isBusy("near")).isFalse();
            assertThat(dispatcher.riderFor("o1")).isEmpty();
            assertThat(riderIdOf(dispatcher.assign(order("o2")))).isEqualTo("near");
        }

        @Test
        void completingTwiceOrAnUnknownOrderReturnsFalse() {
            allDay(NEAR);
            Dispatcher dispatcher = dispatcher(NEAR);
            dispatcher.assign(order("o1"));
            dispatcher.complete("o1");
            dispatcher.assign(order("o2"));

            assertThat(dispatcher.complete("o1")).isFalse(); // must not free near from o2
            assertThat(dispatcher.isBusy("near")).isTrue();
            assertThat(dispatcher.complete("nope")).isFalse();
        }

        @Test
        void aFailedAssignmentLeavesNoTrace() {
            Dispatcher dispatcher = dispatcher(NEAR); // no shifts
            dispatcher.assign(order("o1"));

            assertThat(dispatcher.riderFor("o1")).isEmpty();
            assertThat(dispatcher.complete("o1")).isFalse();
        }

        @Test
        void theSameOrderCannotBeAssignedTwice() {
            allDay(NEAR, MID);
            Dispatcher dispatcher = dispatcher(NEAR, MID);
            dispatcher.assign(order("o1"));

            assertThatThrownBy(() -> dispatcher.assign(order("o1"))).isInstanceOf(IllegalStateException.class);
            assertThat(dispatcher.isBusy("mid")).isFalse();
        }

        @Test
        void rejectsBadInput() {
            assertThatThrownBy(() -> dispatcher(NEAR, NEAR)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Dispatcher(List.of(), schedule, null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> dispatcher().assign(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new Order(" ", RESTAURANT)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> dispatcher().isBusy("ghost")).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
