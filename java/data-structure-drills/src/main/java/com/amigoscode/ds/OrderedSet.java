package com.amigoscode.ds;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * Task 2: a set that remembers insertion order.
 *
 * <p>Pushing a value that is already present is a no-op: the value keeps its original position.
 * {@link #pop()} therefore returns the value whose <em>first</em> push is the most recent among
 * the values still present.
 *
 * <p>Backed by a {@link LinkedHashSet} (a hash table threaded with a doubly linked list), so
 * push, pop, remove and contains are all O(1). Since Java 21 it is a {@code SequencedSet}, which
 * gives {@code removeLast()} directly. Nulls are rejected.
 */
public final class OrderedSet<T> {

    private final LinkedHashSet<T> elements = new LinkedHashSet<>();

    public OrderedSet() {
    }

    @SafeVarargs
    public static <T> OrderedSet<T> of(T... values) {
        OrderedSet<T> set = new OrderedSet<>();
        for (T value : values) {
            set.push(value);
        }
        return set;
    }

    /** Adds the value at the end. Returns {@code false} (and changes nothing) if already present. */
    public boolean push(T value) {
        return elements.add(Objects.requireNonNull(value, "value"));
    }

    /** Removes and returns the most recently inserted value still present. */
    public T pop() {
        if (elements.isEmpty()) {
            throw new NoSuchElementException("pop on an empty set");
        }
        return elements.removeLast();
    }

    /** Returns {@code true} if the value was present. */
    public boolean remove(T value) {
        return elements.remove(value);
    }

    public boolean contains(T value) {
        return elements.contains(value);
    }

    public int size() {
        return elements.size();
    }

    public boolean isEmpty() {
        return elements.isEmpty();
    }

    /** A new set with the values present in both, in this set's insertion order. Neither input changes. */
    public OrderedSet<T> intersect(OrderedSet<T> other) {
        Objects.requireNonNull(other, "other");
        OrderedSet<T> result = new OrderedSet<>();
        for (T value : elements) {
            if (other.contains(value)) {
                result.elements.add(value);
            }
        }
        return result;
    }

    /** An unmodifiable snapshot, no order promised. */
    public Set<T> values() {
        return Set.copyOf(elements);
    }

    /** An unmodifiable snapshot in insertion order. */
    public List<T> orderedValues() {
        return List.copyOf(elements);
    }

    @Override
    public String toString() {
        return elements.toString();
    }
}
