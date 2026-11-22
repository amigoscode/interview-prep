package com.amigoscode.locking.support;

import com.amigoscode.locking.order.OrderConflictException;
import com.amigoscode.locking.order.OrderService;
import com.amigoscode.locking.order.SoldOutException;
import com.amigoscode.locking.order.Strategy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Sends several buyers at OrderService at the same moment, each ordering 1 unit of product 1. */
public final class Buyers {

    public static final long PRODUCT_ID = 1;

    /** What happened to the buyers. {@code unexpected} holds anything that would be a 500. */
    public record Result(int confirmed, int soldOut, int gaveUp, List<Throwable> unexpected) {
    }

    private final ExecutorService executor;
    private final List<Future<?>> futures = new ArrayList<>();

    private Buyers(ExecutorService executor) {
        this.executor = executor;
    }

    /** Starts the buyers and returns immediately. Call {@link #finish()} for the result. */
    public static Buyers start(OrderService orderService, Strategy strategy, int buyers) {
        ExecutorService executor = Executors.newFixedThreadPool(buyers);
        Buyers race = new Buyers(executor);
        CountDownLatch go = new CountDownLatch(1);
        for (int i = 0; i < buyers; i++) {
            race.futures.add(executor.submit(() -> {
                go.await();
                return orderService.placeOrder(strategy, PRODUCT_ID, 1);
            }));
        }
        go.countDown();
        return race;
    }

    /** Starts the buyers and waits for all of them. */
    public static Result run(OrderService orderService, Strategy strategy, int buyers) {
        return start(orderService, strategy, buyers).finish();
    }

    public Result finish() {
        int confirmed = 0;
        int soldOut = 0;
        int gaveUp = 0;
        List<Throwable> unexpected = new ArrayList<>();
        try {
            for (Future<?> future : futures) {
                try {
                    future.get(60, TimeUnit.SECONDS);
                    confirmed++;
                } catch (ExecutionException e) {
                    switch (e.getCause()) {
                        case SoldOutException ignored -> soldOut++;
                        case OrderConflictException ignored -> gaveUp++;
                        default -> unexpected.add(e.getCause());
                    }
                } catch (Exception e) {
                    unexpected.add(e);
                }
            }
        } finally {
            executor.shutdownNow();
        }
        return new Result(confirmed, soldOut, gaveUp, unexpected);
    }
}
