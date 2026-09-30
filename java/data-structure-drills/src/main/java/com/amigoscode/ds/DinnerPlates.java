package com.amigoscode.ds;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.OptionalInt;
import java.util.TreeSet;

/**
 * Task 4: Dinner Plate Stacks (LeetCode 1172).
 *
 * <p>An unbounded row of stacks, each holding at most {@code capacity} plates. {@code push} goes
 * to the leftmost stack that is not full, {@code pop} takes from the rightmost stack that is not
 * empty, {@code popAtStack(i)} takes from stack {@code i}.
 *
 * <p>Two invariants keep every operation at O(log n):
 * <ul>
 *   <li>{@code notFull} holds the index of every existing stack with room, so the leftmost is
 *       {@code notFull.first()}.</li>
 *   <li>The last stack in {@code stacks} is never empty (empty stacks at the right are trimmed),
 *       so {@code pop} always takes from the last stack.</li>
 * </ul>
 *
 * <p>LeetCode returns {@code -1} for "nothing to pop"; this version returns an empty
 * {@link OptionalInt}, because {@code -1} is a perfectly good plate.
 */
public final class DinnerPlates {

    private final int capacity;
    private final List<Deque<Integer>> stacks = new ArrayList<>();
    private final TreeSet<Integer> notFull = new TreeSet<>();

    public DinnerPlates(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive, was " + capacity);
        }
        this.capacity = capacity;
    }

    public void push(int value) {
        int index;
        if (notFull.isEmpty()) {
            index = stacks.size();
            stacks.add(new ArrayDeque<>(capacity));
            notFull.add(index);
        } else {
            index = notFull.first();
        }
        Deque<Integer> stack = stacks.get(index);
        stack.push(value);
        if (stack.size() == capacity) {
            notFull.remove(index);
        }
    }

    public OptionalInt pop() {
        return stacks.isEmpty() ? OptionalInt.empty() : popAtStack(stacks.size() - 1);
    }

    public OptionalInt popAtStack(int index) {
        if (index < 0 || index >= stacks.size() || stacks.get(index).isEmpty()) {
            return OptionalInt.empty();
        }
        int value = stacks.get(index).pop();
        notFull.add(index);
        trimEmptyStacksOnTheRight();
        return OptionalInt.of(value);
    }

    /** Number of stacks currently held (after trimming). Exposed for tests. */
    int stackCount() {
        return stacks.size();
    }

    private void trimEmptyStacksOnTheRight() {
        while (!stacks.isEmpty() && stacks.getLast().isEmpty()) {
            notFull.remove(stacks.size() - 1);
            stacks.removeLast();
        }
    }
}
