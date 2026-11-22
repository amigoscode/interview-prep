package com.amigoscode.idempotency.support;

import com.amigoscode.idempotency.payment.PaymentRequest;
import com.amigoscode.idempotency.payment.PaymentResult;
import com.amigoscode.idempotency.payment.PaymentService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Sends the same request, with the same idempotency key, several times at the same moment. */
public final class Duplicates {

    /** What the copies got back. {@code failures} holds every exception: each would be an error response. */
    public record Result(List<PaymentResult> answers, List<Throwable> failures) {

        public List<Long> paymentIds() {
            return answers.stream().map(answer -> answer.body().paymentId()).distinct().toList();
        }
    }

    private final ExecutorService executor;
    private final List<Future<PaymentResult>> futures = new ArrayList<>();

    private Duplicates(ExecutorService executor) {
        this.executor = executor;
    }

    /** Starts the copies and returns immediately. Call {@link #finish()} for the result. */
    public static Duplicates start(PaymentService paymentService, String key, PaymentRequest request, int copies) {
        ExecutorService executor = Executors.newFixedThreadPool(copies);
        Duplicates duplicates = new Duplicates(executor);
        CountDownLatch go = new CountDownLatch(1);
        for (int i = 0; i < copies; i++) {
            duplicates.futures.add(executor.submit(() -> {
                go.await();
                return paymentService.pay(key, request);
            }));
        }
        go.countDown();
        return duplicates;
    }

    /** Starts the copies and waits for all of them. */
    public static Result send(PaymentService paymentService, String key, PaymentRequest request, int copies) {
        return start(paymentService, key, request, copies).finish();
    }

    public Result finish() {
        List<PaymentResult> answers = new ArrayList<>();
        List<Throwable> failures = new ArrayList<>();
        try {
            for (Future<PaymentResult> future : futures) {
                try {
                    answers.add(future.get(120, TimeUnit.SECONDS));
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                } catch (Exception e) {
                    failures.add(e);
                }
            }
        } finally {
            executor.shutdownNow();
        }
        return new Result(answers, failures);
    }
}
