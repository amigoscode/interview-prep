package com.amigoscode.threadsafety.visits;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

/**
 * Counts visits to the shop. Spring creates ONE VisitCounter and hands that same object to every
 * request thread, so the field below is shared by all of them.
 */
@Service
public class VisitCounter {

    // The reference is final, and AtomicInteger does read-add-write as one step.
    private final AtomicInteger count = new AtomicInteger();

    public void hit() {
        count.incrementAndGet();
    }

    public int count() {
        return count.get();
    }

    public void reset() {
        count.set(0);
    }
}
