package com.amigoscode.arrays;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTimelineTest {

    private final OrderTimeline withDuplicates = new OrderTimeline(new long[] {100, 200, 200, 200, 300});

    @Nested
    class Construction {

        @Test
        void rejectsNullAndUnsortedInput() {
            assertThatThrownBy(() -> new OrderTimeline(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new OrderTimeline(new long[] {1, 3, 2})).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void allowsEqualTimestamps() {
            assertThat(new OrderTimeline(new long[] {5, 5, 5}).countInWindow(5, 6)).isEqualTo(3);
        }

        @Test
        void copiesTheCallersArray() {
            long[] timestamps = {100, 200, 300};
            OrderTimeline timeline = new OrderTimeline(timestamps);
            timestamps[0] = 250;
            assertThat(timeline.countInWindow(0, 150)).isEqualTo(1);
        }
    }

    @Nested
    class FirstAtOrAfter {

        @Test
        void exactMatchReturnsIt() {
            assertThat(withDuplicates.firstAtOrAfter(300)).hasValue(300);
        }

        @Test
        void betweenValuesReturnsTheNextOne() {
            assertThat(withDuplicates.firstAtOrAfter(150)).hasValue(200);
            assertThat(withDuplicates.firstAtOrAfter(201)).hasValue(300);
        }

        @Test
        void beforeEverythingReturnsTheFirst() {
            assertThat(withDuplicates.firstAtOrAfter(Long.MIN_VALUE)).hasValue(100);
            assertThat(withDuplicates.firstAtOrAfter(100)).hasValue(100);
        }

        @Test
        void afterEverythingIsEmpty() {
            assertThat(withDuplicates.firstAtOrAfter(301)).isEqualTo(OptionalLong.empty());
            assertThat(withDuplicates.firstAtOrAfter(Long.MAX_VALUE)).isEmpty();
        }

        @Test
        void emptyTimelineIsEmpty() {
            assertThat(new OrderTimeline(new long[0]).firstAtOrAfter(0)).isEmpty();
        }

        @Test
        void singleOrder() {
            OrderTimeline one = new OrderTimeline(new long[] {1_000});
            assertThat(one.firstAtOrAfter(999)).hasValue(1_000);
            assertThat(one.firstAtOrAfter(1_000)).hasValue(1_000);
            assertThat(one.firstAtOrAfter(1_001)).isEmpty();
        }
    }

    @Nested
    class CountInWindow {

        @Test
        void fromIsInclusiveAndToIsExclusive() {
            assertThat(withDuplicates.countInWindow(100, 300)).isEqualTo(4);
            assertThat(withDuplicates.countInWindow(100, 301)).isEqualTo(5);
            assertThat(withDuplicates.countInWindow(101, 300)).isEqualTo(3);
        }

        @Test
        void countsEveryDuplicateAtTheEdges() {
            assertThat(withDuplicates.countInWindow(200, 201)).isEqualTo(3);
            assertThat(withDuplicates.countInWindow(100, 200)).isEqualTo(1);
            assertThat(withDuplicates.countInWindow(200, 300)).isEqualTo(3);
        }

        @Test
        void adjacentWindowsNeverDoubleCount() {
            int total = withDuplicates.countInWindow(0, 200) + withDuplicates.countInWindow(200, 400);
            assertThat(total).isEqualTo(5);
        }

        @Test
        void windowsOutsideTheDataAreEmpty() {
            assertThat(withDuplicates.countInWindow(0, 100)).isZero();
            assertThat(withDuplicates.countInWindow(301, 1_000)).isZero();
        }

        @Test
        void windowCoveringEverythingCountsEverything() {
            assertThat(withDuplicates.countInWindow(Long.MIN_VALUE, Long.MAX_VALUE)).isEqualTo(5);
        }

        @Test
        void emptyWindowAndEmptyTimelineGiveZero() {
            assertThat(withDuplicates.countInWindow(200, 200)).isZero();
            assertThat(new OrderTimeline(new long[0]).countInWindow(0, 1_000)).isZero();
        }

        @Test
        void rejectsABackwardsWindow() {
            assertThatThrownBy(() -> withDuplicates.countInWindow(300, 100)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void worksAtTheExtremesOfLong() {
            OrderTimeline extremes = new OrderTimeline(new long[] {Long.MIN_VALUE, 0, Long.MAX_VALUE});
            assertThat(extremes.countInWindow(Long.MIN_VALUE, Long.MAX_VALUE)).isEqualTo(2);
            assertThat(extremes.firstAtOrAfter(Long.MIN_VALUE + 1)).hasValue(0);
            assertThat(extremes.firstAtOrAfter(1)).hasValue(Long.MAX_VALUE);
        }

        @Test
        void agreesWithALinearScanOnRealisticTimestamps() {
            long start = 1_790_000_000_000L; // epoch millis, September 2026
            long[] orders = new long[2_000];
            for (int i = 0; i < orders.length; i++) {
                orders[i] = start + (i / 3) * 1_000L; // three orders per second
            }
            OrderTimeline timeline = new OrderTimeline(orders);
            for (long from = start - 2_000; from < start + 20_000; from += 700) {
                long to = from + 2_500;
                long expected = 0;
                for (long ts : orders) {
                    if (ts >= from && ts < to) expected++;
                }
                assertThat(timeline.countInWindow(from, to)).as("[%d, %d)", from, to).isEqualTo(expected);
            }
        }
    }
}
