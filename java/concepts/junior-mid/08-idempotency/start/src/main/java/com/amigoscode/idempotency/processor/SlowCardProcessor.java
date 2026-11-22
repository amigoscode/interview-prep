package com.amigoscode.idempotency.processor;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** A fake card processor: it only waits, and counts how often it was asked to charge. */
@Component
public class SlowCardProcessor implements CardProcessor {

    private final long millis;
    private final long firstCallMillis;
    private final AtomicInteger calls = new AtomicInteger();

    public SlowCardProcessor(@Value("${payments.processor-millis:100}") long millis,
                             @Value("${payments.processor-first-call-millis:-1}") long firstCallMillis) {
        this.millis = millis;
        this.firstCallMillis = firstCallMillis;
    }

    @Override
    public void charge(long customerId, long amountCents) {
        int call = calls.incrementAndGet();
        long wait = call == 1 && firstCallMillis >= 0 ? firstCallMillis : millis;
        if (wait <= 0) {
            return;
        }
        try {
            Thread.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Card processor interrupted", e);
        }
    }

    /** How many times a card was charged since the application started. */
    public int calls() {
        return calls.get();
    }
}
