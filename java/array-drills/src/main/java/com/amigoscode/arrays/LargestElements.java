package com.amigoscode.arrays;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.PriorityQueue;

/**
 * Largest, second largest (distinct) and kth largest (counting duplicates) of an int array.
 *
 * <p>The two "kth" flavours differ on purpose: {@link #secondLargest} answers "the second
 * highest value", so {@code [5, 5, 3]} gives 3, while {@link #kthLargest} follows LeetCode 215
 * and answers "the element in position k if sorted descending", so the same array with k = 2
 * gives 5. Asking which one is meant is part of the exercise.
 */
public final class LargestElements {

    private LargestElements() {
    }

    /** Single pass, O(n) time, O(1) space. */
    public static int largest(int[] nums) {
        requireNonEmpty(nums);
        int max = nums[0];
        for (int i = 1; i < nums.length; i++) {
            max = Math.max(max, nums[i]);
        }
        return max;
    }

    /**
     * The second largest distinct value, or empty when the array holds fewer than two distinct
     * values. Single pass, O(n) time, O(1) space. No sentinel: {@code Integer.MIN_VALUE} is a
     * legitimate answer.
     */
    public static OptionalInt secondLargest(int[] nums) {
        requireNonEmpty(nums);
        int max = nums[0];
        int second = 0;
        boolean hasSecond = false;
        for (int i = 1; i < nums.length; i++) {
            int n = nums[i];
            if (n > max) {
                second = max;
                hasSecond = true;
                max = n;
            } else if (n < max && (!hasSecond || n > second)) {
                second = n;
                hasSecond = true;
            }
        }
        return hasSecond ? OptionalInt.of(second) : OptionalInt.empty();
    }

    /**
     * The kth largest element counting duplicates (k = 1 is the largest). A min-heap holds the k
     * largest seen so far; its head is the kth largest. O(n log k) time, O(k) space.
     */
    public static int kthLargest(int[] nums, int k) {
        requireNonEmpty(nums);
        if (k < 1 || k > nums.length) {
            throw new IllegalArgumentException("k must be between 1 and " + nums.length + ", was " + k);
        }
        PriorityQueue<Integer> heap = new PriorityQueue<>(k + 1);
        for (int n : nums) {
            heap.offer(n);
            if (heap.size() > k) {
                heap.poll();
            }
        }
        return heap.peek();
    }

    private static void requireNonEmpty(int[] nums) {
        Objects.requireNonNull(nums, "nums");
        if (nums.length == 0) {
            throw new IllegalArgumentException("nums must not be empty");
        }
    }
}
