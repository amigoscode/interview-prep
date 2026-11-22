package com.amigoscode.locking.support;

import com.amigoscode.locking.fraud.FraudCheck;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Replaces the real fraud check in integration tests. The fraud check runs between reading the
 * stock and writing it, so controlling it lets a test force the dangerous interleaving on purpose
 * instead of hoping that two threads happen to overlap.
 */
public class ControllableFraudCheck implements FraudCheck {

    private static final long MEETING_TIMEOUT_MILLIS = 1500;

    private final AtomicInteger arrivals = new AtomicInteger();
    private volatile int meetingSize;
    private volatile CountDownLatch meeting;
    private volatile CountDownLatch gate;
    private volatile long delayMillis;

    public void reset() {
        release();
        arrivals.set(0);
        meetingSize = 0;
        meeting = null;
        gate = null;
        delayMillis = 0;
    }

    /**
     * The first {@code buyers} callers wait here until all of them have arrived, so every one of
     * them has already read the stock before any of them writes. If the others never arrive (for
     * example because they are blocked on a row lock) the wait ends after 1.5 seconds.
     */
    public void everyoneReadsBeforeAnyoneWrites(int buyers) {
        meetingSize = buyers;
        meeting = new CountDownLatch(buyers);
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

    /** How many orders have finished reading the stock and reached the fraud check. */
    public int arrivals() {
        return arrivals.get();
    }

    @Override
    public void review(long productId, int quantity) {
        int arrival = arrivals.incrementAndGet();
        try {
            CountDownLatch currentMeeting = meeting;
            if (currentMeeting != null && arrival <= meetingSize) {
                currentMeeting.countDown();
                currentMeeting.await(MEETING_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            }
            CountDownLatch currentGate = gate;
            if (currentGate != null) {
                currentGate.await(20, TimeUnit.SECONDS);
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
