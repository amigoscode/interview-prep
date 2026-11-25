package com.amigoscode.isolation.checkpoint;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/**
 * Used by the demo and the tests instead of {@link NoCheckpoint}: runs an action whenever a
 * checkpoint is reached, on the thread that reached it, which means in the middle of that
 * thread's transaction.
 */
public final class ScriptedCheckpoint implements Checkpoint {

    private final Map<String, AtomicInteger> arrivals = new ConcurrentHashMap<>();
    private final Map<String, IntConsumer> actions = new ConcurrentHashMap<>();

    /** Forget every action and every count. */
    public void reset() {
        arrivals.clear();
        actions.clear();
    }

    /** Runs {@code action} every time {@code name} is reached. It receives the arrival number: 1, 2, 3... */
    public void onReached(String name, IntConsumer action) {
        actions.put(name, action);
    }

    /** Runs {@code action} the first time {@code name} is reached, and never again (so not on a retry). */
    public void onFirst(String name, Runnable action) {
        onReached(name, arrival -> {
            if (arrival == 1) {
                action.run();
            }
        });
    }

    /** How many times {@code name} has been reached. */
    public int arrivals(String name) {
        AtomicInteger count = arrivals.get(name);
        return count == null ? 0 : count.get();
    }

    @Override
    public void reached(String name) {
        int arrival = arrivals.computeIfAbsent(name, key -> new AtomicInteger()).incrementAndGet();
        IntConsumer action = actions.get(name);
        if (action != null) {
            action.accept(arrival);
        }
    }
}
