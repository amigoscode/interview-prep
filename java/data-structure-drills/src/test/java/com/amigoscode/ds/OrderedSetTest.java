package com.amigoscode.ds;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderedSetTest {

    @Test
    void popReturnsTheLastInsertedValue() {
        OrderedSet<String> set = new OrderedSet<>();
        set.push("a");
        set.push("b");
        set.push("c");
        assertThat(set.pop()).isEqualTo("c");
        assertThat(set.pop()).isEqualTo("b");
        assertThat(set.orderedValues()).containsExactly("a");
    }

    @Nested
    class Push {

        @Test
        void keepsInsertionOrder() {
            OrderedSet<Integer> set = OrderedSet.of(3, 1, 2);
            assertThat(set.orderedValues()).containsExactly(3, 1, 2);
            assertThat(set.size()).isEqualTo(3);
        }

        @Test
        void duplicatePushIsANoOpAndKeepsTheOriginalPosition() {
            OrderedSet<String> set = OrderedSet.of("a", "b");
            assertThat(set.push("a")).isFalse();
            assertThat(set.orderedValues()).containsExactly("a", "b");
            assertThat(set.pop()).isEqualTo("b");
        }

        @Test
        void rejectsNull() {
            assertThatThrownBy(() -> new OrderedSet<String>().push(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class Pop {

        @Test
        void onAnEmptySetThrows() {
            assertThatThrownBy(() -> new OrderedSet<String>().pop()).isInstanceOf(NoSuchElementException.class);
        }

        @Test
        void skipsValuesThatWereRemoved() {
            OrderedSet<String> set = OrderedSet.of("a", "b", "c");
            set.remove("c");
            assertThat(set.pop()).isEqualTo("b");
        }

        @Test
        void aValuePushedAgainAfterRemovalGoesToTheEnd() {
            OrderedSet<String> set = OrderedSet.of("a", "b", "c");
            set.remove("a");
            set.push("a");
            assertThat(set.orderedValues()).containsExactly("b", "c", "a");
            assertThat(set.pop()).isEqualTo("a");
        }

        @Test
        void drainsInReverseInsertionOrder() {
            OrderedSet<Integer> set = OrderedSet.of(1, 2, 3);
            assertThat(set.pop()).isEqualTo(3);
            assertThat(set.pop()).isEqualTo(2);
            assertThat(set.pop()).isEqualTo(1);
            assertThat(set.isEmpty()).isTrue();
        }
    }

    @Nested
    class Remove {

        @Test
        void removesFromTheMiddleAndKeepsTheRestInOrder() {
            OrderedSet<String> set = OrderedSet.of("a", "b", "c");
            assertThat(set.remove("b")).isTrue();
            assertThat(set.contains("b")).isFalse();
            assertThat(set.orderedValues()).containsExactly("a", "c");
        }

        @Test
        void removingAnAbsentValueReturnsFalse() {
            OrderedSet<String> set = OrderedSet.of("a");
            assertThat(set.remove("z")).isFalse();
            assertThat(set.orderedValues()).containsExactly("a");
        }
    }

    @Nested
    class Intersect {

        @Test
        void returnsANewSetInThisSetsOrder() {
            OrderedSet<Integer> left = OrderedSet.of(1, 2, 3, 4);
            OrderedSet<Integer> right = OrderedSet.of(4, 9, 2);

            OrderedSet<Integer> both = left.intersect(right);

            assertThat(both.orderedValues()).containsExactly(2, 4);
            assertThat(both).isNotSameAs(left).isNotSameAs(right);
        }

        @Test
        void leavesBothInputsUntouched() {
            OrderedSet<Integer> left = OrderedSet.of(1, 2);
            OrderedSet<Integer> right = OrderedSet.of(2, 3);
            OrderedSet<Integer> both = left.intersect(right);

            both.push(99);
            both.pop();
            both.pop();

            assertThat(left.orderedValues()).containsExactly(1, 2);
            assertThat(right.orderedValues()).containsExactly(2, 3);
        }

        @Test
        void disjointAndEmptySetsGiveAnEmptySet() {
            assertThat(OrderedSet.of(1, 2).intersect(OrderedSet.of(3)).isEmpty()).isTrue();
            assertThat(OrderedSet.of(1, 2).intersect(new OrderedSet<>()).isEmpty()).isTrue();
            assertThat(new OrderedSet<Integer>().intersect(OrderedSet.of(1)).isEmpty()).isTrue();
        }

        @Test
        void theResultSupportsPopInItsOwnOrder() {
            OrderedSet<String> both = OrderedSet.of("x", "y", "z").intersect(OrderedSet.of("z", "x"));
            assertThat(both.pop()).isEqualTo("z");
        }
    }

    @Nested
    class Values {

        @Test
        void valuesContainsEverythingInAnyOrder() {
            OrderedSet<String> set = OrderedSet.of("c", "a", "b");
            assertThat(set.values()).containsExactlyInAnyOrder("a", "b", "c");
        }

        @Test
        void snapshotsAreUnmodifiableAndDetached() {
            OrderedSet<String> set = OrderedSet.of("a");
            var values = set.values();
            var ordered = set.orderedValues();

            set.push("b");

            assertThat(values).containsExactly("a");
            assertThat(ordered).containsExactly("a");
            assertThatThrownBy(() -> ordered.add("x")).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> values.add("x")).isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
