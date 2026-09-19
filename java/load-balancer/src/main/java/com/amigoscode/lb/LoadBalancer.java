package com.amigoscode.lb;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Holds up to {@code capacity} unique addresses and hands one out per {@link #get()}.
 *
 * <p>LinkedHashSet gives O(1) duplicate checks (hash to a bucket, then equals inside the
 * bucket) and keeps registration order, which round robin needs. The critical sections are a
 * few operations on a small set, so {@code synchronized} is the right tool: simple, and
 * contention is negligible. A read-heavy variant would use a ReadWriteLock or
 * CopyOnWriteArraySet so that {@code get()} never blocks.
 */
public final class LoadBalancer {

    private final int capacity;
    private final SelectionStrategy strategy;
    private final Set<String> addresses = new LinkedHashSet<>(); // guarded by this

    public LoadBalancer(int capacity) {
        this(capacity, new RandomSelection());
    }

    public LoadBalancer(int capacity, SelectionStrategy strategy) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.strategy = Objects.requireNonNull(strategy, "strategy");
    }

    public synchronized boolean register(String address) {
        Objects.requireNonNull(address, "address");
        if (addresses.size() >= capacity) {
            return false;
        }
        return addresses.add(address); // false when already present
    }

    public synchronized boolean unregister(String address) {
        return addresses.remove(address);
    }

    public synchronized String get() {
        if (addresses.isEmpty()) {
            throw new NoInstancesException();
        }
        return strategy.select(List.copyOf(addresses));
    }

    public synchronized int size() {
        return addresses.size();
    }
}
