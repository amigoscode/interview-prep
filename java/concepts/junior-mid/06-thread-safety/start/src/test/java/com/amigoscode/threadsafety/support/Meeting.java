package com.amigoscode.threadsafety.support;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A place in the code where threads wait for each other. The test stubs put a Meeting inside the
 * slow call that sits between a bean's READ and its WRITE. That forces the dangerous interleaving
 * on purpose, every time, instead of hoping that two threads happen to overlap.
 *
 * The first {@code size} threads to arrive wait until all of them are here. If the others cannot
 * come because the code under test makes them wait (a lock, or ConcurrentHashMap.computeIfAbsent),
 * the meeting notices that they are blocked and lets the ones who are here carry on.
 */
public final class Meeting {

    private static final long SAMPLE_MILLIS = 20;
    private static final int BLOCKED_SAMPLES_NEEDED = 5;
    private static final long GIVE_UP_SECONDS = 15;

    private final int size;
    private final CountDownLatch everyoneIsHere;
    private final AtomicInteger arrivals = new AtomicInteger();
    private final Set<Thread> here = ConcurrentHashMap.newKeySet();
    private volatile Thread[] racers;

    public Meeting(int size) {
        this.size = size;
        this.everyoneIsHere = new CountDownLatch(size);
    }

    /** Called by {@link Race}: the threads that are expected to turn up. */
    void racers(Thread[] racers) {
        this.racers = racers;
    }

    /** How many threads have reached the meeting point so far. */
    public int arrivals() {
        return arrivals.get();
    }

    /** Called from inside the stub. Returns when everyone has arrived, or when nobody else can. */
    public void arrive() {
        if (arrivals.incrementAndGet() > size) {
            return;
        }
        here.add(Thread.currentThread());
        everyoneIsHere.countDown();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(GIVE_UP_SECONDS);
        int blockedSamples = 0;
        try {
            while (!everyoneIsHere.await(SAMPLE_MILLIS, TimeUnit.MILLISECONDS)) {
                blockedSamples = nobodyElseCanCome() ? blockedSamples + 1 : 0;
                if (blockedSamples >= BLOCKED_SAMPLES_NEEDED || System.nanoTime() > deadline) {
                    return;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** True when every racer that is not at the meeting is blocked, waiting or finished. */
    private boolean nobodyElseCanCome() {
        Thread[] expected = racers;
        if (expected == null) {
            return false;
        }
        for (Thread racer : expected) {
            if (here.contains(racer)) {
                continue;
            }
            Thread.State state = racer.getState();
            if (state == Thread.State.NEW || state == Thread.State.RUNNABLE) {
                return false;
            }
        }
        return true;
    }
}
