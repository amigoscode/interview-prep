package com.amigoscode.constraints.support;

import com.amigoscode.constraints.work.SlowWork;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Replaces the real slow work in integration tests. The work runs between the check and the insert,
 * so controlling it lets a test force the dangerous interleaving on purpose instead of hoping that
 * two threads happen to overlap.
 */
public class ControllableWork implements SlowWork {

    private static final long MEETING_TIMEOUT_MILLIS = 1500;

    private final AtomicInteger arrivals = new AtomicInteger();
    private volatile int meetingSize;
    private volatile CountDownLatch meeting;
    private volatile long delayMillis;

    public void reset() {
        arrivals.set(0);
        meetingSize = 0;
        meeting = null;
        delayMillis = 0;
    }

    /**
     * The first {@code requests} callers wait here until all of them have arrived, so every one of
     * them has already passed the check before any of them inserts. If the others never arrive the
     * wait ends after 1.5 seconds.
     */
    public void everyonePassesTheCheckBeforeAnyoneInserts(int requests) {
        meetingSize = requests;
        meeting = new CountDownLatch(requests);
    }

    public void takes(long millis) {
        delayMillis = millis;
    }

    /** How many requests got past the check and reached the slow work. */
    public int arrivals() {
        return arrivals.get();
    }

    @Override
    public void run(String step) {
        int arrival = arrivals.incrementAndGet();
        try {
            CountDownLatch currentMeeting = meeting;
            if (currentMeeting != null && arrival <= meetingSize) {
                currentMeeting.countDown();
                currentMeeting.await(MEETING_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
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
