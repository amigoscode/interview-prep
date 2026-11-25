package com.amigoscode.constraints.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

/** Runs several calls at the same moment, each on its own thread, and collects what happened. */
public final class Race {

    /** {@code succeeded} holds the return values, {@code failed} the exceptions. */
    public record Result<T>(List<T> succeeded, List<Throwable> failed) {

        /** How many calls failed with this kind of exception. */
        public int failedWith(Class<? extends Throwable> type) {
            return (int) failed.stream().filter(type::isInstance).count();
        }

        /** Failures that are not of this kind: anything here would be a 500. */
        public List<Throwable> failedWithAnythingBut(Class<? extends Throwable> expected) {
            return failed.stream().filter(failure -> !expected.isInstance(failure)).toList();
        }
    }

    /** Runs {@code calls} copies of the same call. */
    public static <T> Result<T> run(int calls, Callable<T> call) {
        return run(calls, i -> call);
    }

    /** Runs {@code calls} different calls: {@code call.apply(0)}, {@code call.apply(1)}, ... */
    public static <T> Result<T> run(int calls, IntFunction<Callable<T>> call) {
        ExecutorService executor = Executors.newFixedThreadPool(calls);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (int i = 0; i < calls; i++) {
            Callable<T> one = call.apply(i);
            futures.add(executor.submit(() -> {
                go.await();
                return one.call();
            }));
        }
        go.countDown();

        List<T> succeeded = new ArrayList<>();
        List<Throwable> failed = new ArrayList<>();
        try {
            for (Future<T> future : futures) {
                try {
                    succeeded.add(future.get(60, TimeUnit.SECONDS));
                } catch (ExecutionException e) {
                    failed.add(e.getCause());
                } catch (Exception e) {
                    failed.add(e);
                }
            }
        } finally {
            executor.shutdownNow();
        }
        return new Result<>(succeeded, failed);
    }

    private Race() {
    }
}
