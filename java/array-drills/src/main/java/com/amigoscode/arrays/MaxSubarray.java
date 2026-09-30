package com.amigoscode.arrays;

import java.util.Objects;

/**
 * Maximum subarray sum (LeetCode 53) with Kadane's algorithm: O(n) time, O(1) extra space.
 *
 * <p>Sums are {@code long} so that large ints cannot overflow. The best sum starts at the first
 * element, not at 0, so an all-negative array returns its largest element.
 */
public final class MaxSubarray {

    private MaxSubarray() {
    }

    public static long maxSubarraySum(int[] nums) {
        return maxSubarray(nums).sum();
    }

    /**
     * The maximum-sum subarray with inclusive indices. Ties go to the subarray that ends first,
     * and among those to the shortest: a running sum that is not positive is dropped, and the
     * best is only replaced by a strictly larger sum.
     */
    public static Subarray maxSubarray(int[] nums) {
        Objects.requireNonNull(nums, "nums");
        if (nums.length == 0) {
            throw new IllegalArgumentException("nums must not be empty");
        }
        long current = nums[0];
        int currentStart = 0;
        long best = current;
        int bestStart = 0;
        int bestEnd = 0;
        for (int i = 1; i < nums.length; i++) {
            if (current <= 0) {
                current = nums[i];
                currentStart = i;
            } else {
                current += nums[i];
            }
            if (current > best) {
                best = current;
                bestStart = currentStart;
                bestEnd = i;
            }
        }
        return new Subarray(best, bestStart, bestEnd);
    }
}
