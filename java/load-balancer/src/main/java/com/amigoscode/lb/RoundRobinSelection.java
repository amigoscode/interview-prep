package com.amigoscode.lb;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cycles through the addresses in order. The counter is atomic so it is safe even if the
 * strategy were called outside the balancer's lock; floorMod keeps it correct after the int
 * wraps around, which it will after two billion requests.
 */
public final class RoundRobinSelection implements SelectionStrategy {

    private final AtomicInteger next = new AtomicInteger();

    @Override
    public String select(List<String> addresses) {
        int index = Math.floorMod(next.getAndIncrement(), addresses.size());
        return addresses.get(index);
    }
}
