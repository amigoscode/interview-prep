package com.amigoscode.ds;

import java.util.List;
import java.util.Set;

/**
 * Task 2 starts here. A set that remembers insertion order. Decide (and document) what a
 * duplicate push does: no-op, or move the value to the end.
 */
public final class OrderedSet<T> {

    public OrderedSet() {
        throw new UnsupportedOperationException("story 2");
    }

    @SafeVarargs
    public static <T> OrderedSet<T> of(T... values) {
        throw new UnsupportedOperationException("story 2");
    }

    /** Adds the value at the end. Returns {@code false} if it was already present. */
    public boolean push(T value) {
        throw new UnsupportedOperationException("story 2");
    }

    /** Removes and returns the most recently inserted value still present. */
    public T pop() {
        throw new UnsupportedOperationException("story 2");
    }

    /** Returns {@code true} if the value was present. */
    public boolean remove(T value) {
        throw new UnsupportedOperationException("story 2");
    }

    public boolean contains(T value) {
        throw new UnsupportedOperationException("story 2");
    }

    public int size() {
        throw new UnsupportedOperationException("story 2");
    }

    public boolean isEmpty() {
        throw new UnsupportedOperationException("story 2");
    }

    /** A new set with the values present in both. Neither input changes. */
    public OrderedSet<T> intersect(OrderedSet<T> other) {
        throw new UnsupportedOperationException("story 2");
    }

    /** Every value, any order. */
    public Set<T> values() {
        throw new UnsupportedOperationException("story 2");
    }

    /** Every value, in insertion order. */
    public List<T> orderedValues() {
        throw new UnsupportedOperationException("story 2");
    }
}
