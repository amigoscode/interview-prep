package com.amigoscode.arrays;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static com.amigoscode.arrays.LargestElements.kthLargest;
import static com.amigoscode.arrays.LargestElements.largest;
import static com.amigoscode.arrays.LargestElements.secondLargest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LargestElementsTest {

    @Nested
    class Largest {

        @Test
        void findsTheLargestElement() {
            assertThat(largest(new int[] {3, 9, -2, 7})).isEqualTo(9);
        }

        @Test
        void handlesOneElementNegativesAndExtremes() {
            assertThat(largest(new int[] {42})).isEqualTo(42);
            assertThat(largest(new int[] {-5, -1, -9})).isEqualTo(-1);
            assertThat(largest(new int[] {Integer.MIN_VALUE, Integer.MAX_VALUE})).isEqualTo(Integer.MAX_VALUE);
            assertThat(largest(new int[] {Integer.MIN_VALUE})).isEqualTo(Integer.MIN_VALUE);
        }

        @Test
        void rejectsEmptyAndNull() {
            assertThatThrownBy(() -> largest(new int[0])).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> largest(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class SecondLargest {

        @Test
        void findsTheSecondLargest() {
            assertThat(secondLargest(new int[] {3, 9, -2, 7})).hasValue(7);
        }

        @Test
        void countsDistinctValuesOnly() {
            assertThat(secondLargest(new int[] {5, 5, 3})).hasValue(3);
            assertThat(secondLargest(new int[] {3, 5, 5})).hasValue(3);
            assertThat(secondLargest(new int[] {9, 1, 9, 4, 9})).hasValue(4);
        }

        @Test
        void worksWhenTheMaximumComesFirstOrLast() {
            assertThat(secondLargest(new int[] {10, 1, 2, 8})).hasValue(8);
            assertThat(secondLargest(new int[] {1, 2, 8, 10})).hasValue(8);
        }

        @Test
        void isEmptyWhenThereIsNoSecondDistinctValue() {
            assertThat(secondLargest(new int[] {7})).isEqualTo(OptionalInt.empty());
            assertThat(secondLargest(new int[] {7, 7, 7})).isEmpty();
        }

        @Test
        void minValueIsALegitimateAnswerNotASentinel() {
            assertThat(secondLargest(new int[] {Integer.MIN_VALUE, 5})).hasValue(Integer.MIN_VALUE);
            assertThat(secondLargest(new int[] {5, Integer.MIN_VALUE, Integer.MIN_VALUE})).hasValue(Integer.MIN_VALUE);
        }

        @Test
        void handlesNegatives() {
            assertThat(secondLargest(new int[] {-4, -1, -7, -1})).hasValue(-4);
        }

        @Test
        void rejectsEmptyAndNull() {
            assertThatThrownBy(() -> secondLargest(new int[0])).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> secondLargest(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class KthLargest {

        @Test
        void findsTheKthLargest() {
            int[] nums = {3, 2, 1, 5, 6, 4};
            assertThat(kthLargest(nums, 1)).isEqualTo(6);
            assertThat(kthLargest(nums, 2)).isEqualTo(5);
            assertThat(kthLargest(nums, 6)).isEqualTo(1);
        }

        @Test
        void countsDuplicatesUnlikeSecondLargest() {
            assertThat(kthLargest(new int[] {5, 5, 3}, 2)).isEqualTo(5);
            assertThat(kthLargest(new int[] {3, 2, 3, 1, 2, 4, 5, 5, 6}, 4)).isEqualTo(4);
        }

        @Test
        void kOfOneIsTheLargestAndKOfNIsTheSmallest() {
            int[] nums = {-1, 8, 0, 8, -20, 3};
            assertThat(kthLargest(nums, 1)).isEqualTo(largest(nums));
            assertThat(kthLargest(nums, nums.length)).isEqualTo(-20);
        }

        @Test
        void doesNotReorderTheCallersArray() {
            int[] nums = {3, 1, 2};
            kthLargest(nums, 2);
            assertThat(nums).containsExactly(3, 1, 2);
        }

        @Test
        void rejectsKOutOfRangeEmptyAndNull() {
            int[] nums = {1, 2, 3};
            assertThatThrownBy(() -> kthLargest(nums, 0)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> kthLargest(nums, 4)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> kthLargest(new int[0], 1)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> kthLargest(null, 1)).isInstanceOf(NullPointerException.class);
        }
    }
}
