package com.amigoscode.ds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Task 3: a fixed-capacity least-recently-used cache.
 *
 * <p>A {@link HashMap} finds the node for a key in O(1); a hand-rolled doubly linked list keeps
 * the nodes in recency order (most recent next to {@code head}, least recent next to {@code tail})
 * so moving a node and evicting the oldest are O(1) too. Sentinel head and tail nodes remove every
 * null check from the list code.
 *
 * <p>Both {@code get} and {@code put} change the recency order, so even reads mutate the list.
 * Every public method is {@code synchronized}: one lock, simple and correct. See INTERVIEW_TASKS
 * for why a {@code ReadWriteLock} does not help here.
 */
public final class LruCache<K, V> {

    private static final class Node<K, V> {
        final K key;
        V value;
        Node<K, V> prev;
        Node<K, V> next;

        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    private final int capacity;
    private final Map<K, Node<K, V>> index;
    private final Node<K, V> head = new Node<>(null, null);
    private final Node<K, V> tail = new Node<>(null, null);

    public LruCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive, was " + capacity);
        }
        this.capacity = capacity;
        this.index = HashMap.newHashMap(capacity);
        head.next = tail;
        tail.prev = head;
    }

    /** Returns the value and marks the key as most recently used. */
    public synchronized Optional<V> get(K key) {
        Node<K, V> node = index.get(Objects.requireNonNull(key, "key"));
        if (node == null) {
            return Optional.empty();
        }
        moveToFront(node);
        return Optional.of(node.value);
    }

    /** Inserts or updates, marks the key as most recently used, evicts the least recent if over capacity. */
    public synchronized void put(K key, V value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Node<K, V> existing = index.get(key);
        if (existing != null) {
            existing.value = value;
            moveToFront(existing);
            return;
        }
        if (index.size() == capacity) {
            Node<K, V> eldest = tail.prev;
            unlink(eldest);
            index.remove(eldest.key);
        }
        Node<K, V> node = new Node<>(key, value);
        linkAfterHead(node);
        index.put(key, node);
    }

    public synchronized int size() {
        return index.size();
    }

    public int capacity() {
        return capacity;
    }

    /** Keys from most to least recently used. Does not change the order. */
    public synchronized List<K> keysByRecency() {
        List<K> keys = new ArrayList<>(index.size());
        for (Node<K, V> n = head.next; n != tail; n = n.next) {
            keys.add(n.key);
        }
        return List.copyOf(keys);
    }

    private void moveToFront(Node<K, V> node) {
        unlink(node);
        linkAfterHead(node);
    }

    private void unlink(Node<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private void linkAfterHead(Node<K, V> node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }
}
