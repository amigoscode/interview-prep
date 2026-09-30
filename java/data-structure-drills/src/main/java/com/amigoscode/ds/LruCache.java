package com.amigoscode.ds;

import java.util.List;
import java.util.Optional;

/**
 * Task 3 starts here. A fixed-capacity least-recently-used cache with O(1) get and put.
 * Build it yourself (HashMap + doubly linked list), not with LinkedHashMap.
 */
public final class LruCache<K, V> {

    public LruCache(int capacity) {
        throw new UnsupportedOperationException("story 3");
    }

    /** Returns the value and marks the key as most recently used. */
    public Optional<V> get(K key) {
        throw new UnsupportedOperationException("story 3");
    }

    /** Inserts or updates, marks the key as most recently used, evicts the least recent if over capacity. */
    public void put(K key, V value) {
        throw new UnsupportedOperationException("story 3");
    }

    public int size() {
        throw new UnsupportedOperationException("story 3");
    }

    public int capacity() {
        throw new UnsupportedOperationException("story 3");
    }

    /** Keys from most to least recently used. Does not change the order. */
    public List<K> keysByRecency() {
        throw new UnsupportedOperationException("story 3");
    }
}
