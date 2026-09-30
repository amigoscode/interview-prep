package com.amigoscode.arrays;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.amigoscode.arrays.MaxSubarray.maxSubarray;
import static com.amigoscode.arrays.MaxSubarray.maxSubarraySum;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaxSubarrayTest {

    @Nested
    class Sum {

        @Test
        void leetCodeExample() {
            assertThat(maxSubarraySum(new int[] {-2, 1, -3, 4, -1, 2, 1, -5, 4})).isEqualTo(6);
        }

        @Test
        void singleElement() {
            assertThat(maxSubarraySum(new int[] {1})).isEqualTo(1);
            assertThat(maxSubarraySum(new int[] {-7})).isEqualTo(-7);
        }

        @Test
        void allNegativeReturnsTheLargestElementNotZero() {
            assertThat(maxSubarraySum(new int[] {-3, -1, -2})).isEqualTo(-1);
            assertThat(maxSubarraySum(new int[] {Integer.MIN_VALUE, Integer.MIN_VALUE})).isEqualTo(Integer.MIN_VALUE);
        }

        @Test
        void allPositiveIsTheWholeArray() {
            assertThat(maxSubarraySum(new int[] {5, 4, -1, 7, 8})).isEqualTo(23);
        }

        @Test
        void doesNotOverflow() {
            assertThat(maxSubarraySum(new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE}))
                    .isEqualTo(2L * Integer.MAX_VALUE);
        }

        @Test
        void rejectsEmptyAndNull() {
            assertThatThrownBy(() -> maxSubarraySum(new int[0])).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> maxSubarraySum(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class StretchIndices {

        @Test
        void returnsInclusiveStartAndEnd() {
            assertThat(maxSubarray(new int[] {-2, 1, -3, 4, -1, 2, 1, -5, 4}))
                    .isEqualTo(new Subarray(6, 3, 6));
        }

        @Test
        void allNegativePointsAtTheLargestElement() {
            assertThat(maxSubarray(new int[] {-3, -1, -2})).isEqualTo(new Subarray(-1, 1, 1));
        }

        @Test
        void wholeArrayWhenEverythingHelps() {
            assertThat(maxSubarray(new int[] {5, 4, -1, 7, 8})).isEqualTo(new Subarray(23, 0, 4));
        }

        @Test
        void tiesGoToTheSubarrayThatEndsFirst() {
            assertThat(maxSubarray(new int[] {3, -5, 3})).isEqualTo(new Subarray(3, 0, 0));
        }

        @Test
        void tiesWithTheSameEndGoToTheShortest() {
            assertThat(maxSubarray(new int[] {2, -2, 5})).isEqualTo(new Subarray(5, 2, 2));
            assertThat(maxSubarray(new int[] {0, 0, 5})).isEqualTo(new Subarray(5, 2, 2));
        }

        @Test
        void sumAlwaysMatchesTheIndices() {
            int[] nums = {4, -6, 2, 2, -1, 3, -9, 1};
            Subarray best = maxSubarray(nums);
            long sum = 0;
            for (int i = best.start(); i <= best.end(); i++) {
                sum += nums[i];
            }
            assertThat(best).isEqualTo(new Subarray(6, 2, 5));
            assertThat(sum).isEqualTo(best.sum());
        }
    }
}
