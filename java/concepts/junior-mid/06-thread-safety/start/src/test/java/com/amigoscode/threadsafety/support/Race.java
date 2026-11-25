package com.amigoscode.threadsafety.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs the same piece of code on several threads at the same moment. This is what a web server
 * does to a singleton bean all day: many request threads, one object.
 */
public final class Race {

    private static final long LIMIT_SECONDS = 20;

    /**
     * Never set. Reading a volatile field between two calls stops the JIT compiler from merging
     * them: without it, a loop of 100,000 count++ calls can be compiled into one "count += 100000",
     * which hides the very race these tests are looking for.
     */
    private static volatile boolean stop;

    /** What one thread does, once. {@code thread} counts from 0, {@code round} counts from 0. */
    @FunctionalInterface
    public interface Step {
        void run(int thread, int round) throws Exception;
    }

    private Race() {
    }

    /**
     * Starts {@code threads} threads. Each one calls {@code step} {@code timesEach} times. Nobody
     * starts until everybody is ready. Returns whatever the threads threw: an empty list means
     * every call returned normally.
     */
    public static List<Throwable> run(int threads, int timesEach, Step step) {
        return run(null, threads, timesEach, step);
    }

    /** The same, and tells {@code meeting} who is racing, so it can see when the others are blocked. */
    public static List<Throwable> run(Meeting meeting, int threads, int timesEach, Step step) {
        List<Throwable> thrown = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch ready = new CountDownLatch(threads);
        AtomicBoolean go = new AtomicBoolean();
        Thread[] racers = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            int thread = t;
            racers[t] = new Thread(() -> {
                ready.countDown();
                // Spin instead of sleeping: a spinning thread is RUNNABLE and starts within nanoseconds.
                while (!go.get()) {
                    Thread.onSpinWait();
                }
                try {
                    for (int round = 0; round < timesEach && !stop; round++) {
                        step.run(thread, round);
                    }
                } catch (Throwable e) {
                    thrown.add(e);
                }
            }, "racer-" + (t + 1));
            racers[t].setDaemon(true);
        }
        if (meeting != null) {
            meeting.racers(racers);
        }
        for (Thread racer : racers) {
            racer.start();
        }
        try {
            ready.await();
            go.set(true);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(LIMIT_SECONDS);
            for (Thread racer : racers) {
                long millisLeft = Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
                racer.join(millisLeft);
                if (racer.isAlive()) {
                    thrown.add(new AssertionError(racer.getName() + " did not finish within " + LIMIT_SECONDS
                            + " seconds. It is stuck in " + whereIs(racer)));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            thrown.add(e);
        }
        return new ArrayList<>(thrown);
    }

    /** One short line per distinct problem, instead of a list of stack traces. */
    public static List<String> describe(List<Throwable> thrown) {
        return thrown.stream()
                .map(e -> e instanceof AssertionError ? e.getMessage() : e.getClass().getName() + " in " + whereFrom(e))
                .distinct()
                .toList();
    }

    private static String whereFrom(Throwable e) {
        return e.getStackTrace().length == 0 ? "(unknown)" : e.getStackTrace()[0].toString();
    }

    private static String whereIs(Thread thread) {
        StackTraceElement[] stack = thread.getStackTrace();
        return stack.length == 0 ? "(unknown)" : stack[0].toString();
    }
}
