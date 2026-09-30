package com.amigoscode.arrays;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Index pairs {@code i < j} with {@code nums[i] + nums[j] == k}.
 *
 * <p>Spec: every such pair, an index may appear in many pairs, result sorted by i then j. (The
 * example in the interview report is ambiguous; see INTERVIEW_TASKS.md.)
 *
 * <p>One pass, right to left: for each i the map already holds every later index grouped by
 * value (in descending order, because they were appended right to left), so each pair is found
 * exactly once, at its i. Reversing the collected list then gives ascending i, and within an i
 * ascending j, without a sort. The complement is computed in {@code long} so that
 * {@code k - nums[i]} cannot overflow into a false match.
 */
public final class PairSum {

    private PairSum() {
    }

    /** O(n + p) time and space for p pairs. p can be n²/2 when every element is equal. */
    public static List<IndexPair> pairsWithSum(int[] nums, int k) {
        Objects.requireNonNull(nums, "nums");
        Map<Integer, List<Integer>> later = new HashMap<>();
        List<IndexPair> pairs = new ArrayList<>();
        for (int i = nums.length - 1; i >= 0; i--) {
            long complement = (long) k - nums[i];
            if (fitsInInt(complement)) {
                for (int j : later.getOrDefault((int) complement, List.of())) {
                    pairs.add(new IndexPair(i, j));
                }
            }
            later.computeIfAbsent(nums[i], v -> new ArrayList<>()).add(i);
        }
        return List.copyOf(pairs.reversed());
    }

    /** O(n) time, O(n) space: a count per value instead of a list of indices. */
    public static long countPairs(int[] nums, int k) {
        Objects.requireNonNull(nums, "nums");
        Map<Integer, Integer> seen = new HashMap<>();
        long count = 0;
        for (int n : nums) {
            long complement = (long) k - n;
            if (fitsInInt(complement)) {
                count += seen.getOrDefault((int) complement, 0);
            }
            seen.merge(n, 1, Integer::sum);
        }
        return count;
    }

    private static boolean fitsInInt(long value) {
        return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE;
    }
}
