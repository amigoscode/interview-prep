package com.amigoscode.arrays;

/** A contiguous run {@code nums[start..end]}, both ends inclusive, and its sum. */
public record Subarray(long sum, int start, int end) {
}
