package com.amigoscode.idempotency.support;

import com.amigoscode.idempotency.processor.CardProcessor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Replaces the real card processor in integration tests. The processor runs after the key has been
 * checked and before the payment is written, so controlling it lets a test force the dangerous
 * interleaving on purpose instead of hoping that two threads happen to overlap. It also counts its
 * calls: a card that was charged twice is a double charge, whatever the database says afterwards.
 */
public class ControllableCardProcessor implements CardProcessor {

    private static final long MEETING_TIMEOUT_MILLIS = 3000;

    private final AtomicInteger calls = new AtomicInteger();
    private volatile int meetingSize;
    private volatile CountDownLatch meeting;
    private volatile CountDownLatch gate;
    private volatile long delayMillis;

    public void reset() {
        release();
        calls.set(0);
        meetingSize = 0;
        meeting = null;
        gate = null;
        delayMillis = 0;
    }

    /**
     * The first {@code requests} callers wait here until all of them have arrived, so every one of
     * them has already passed the key check before any of them writes its payment. If the others
     * never arrive (for example because the database makes them wait) the wait ends after 3 seconds.
     */
    public void everyoneChecksTheKeyBeforeAnyoneCharges(int requests) {
        meetingSize = requests;
        meeting = new CountDownLatch(requests);
    }

    /** Every caller stops here until {@link #release()}. */
    public void holdUntilReleased() {
        gate = new CountDownLatch(1);
    }

    public void release() {
        CountDownLatch current = gate;
        if (current != null) {
            current.countDown();
        }
    }

    public void takes(long millis) {
        delayMillis = millis;
    }

    /** How many times the card was charged. */
    public int calls() {
        return calls.get();
    }

    @Override
    public void charge(long customerId, long amountCents) {
        int call = calls.incrementAndGet();
        try {
            CountDownLatch currentMeeting = meeting;
            if (currentMeeting != null && call <= meetingSize) {
                currentMeeting.countDown();
                currentMeeting.await(MEETING_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            }
            CountDownLatch currentGate = gate;
            if (currentGate != null) {
                currentGate.await(30, TimeUnit.SECONDS);
            }
            if (delayMillis > 0) {
                Thread.sleep(delayMillis);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
