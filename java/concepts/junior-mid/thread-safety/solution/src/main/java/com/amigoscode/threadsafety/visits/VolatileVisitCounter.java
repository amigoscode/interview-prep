package com.amigoscode.threadsafety.visits;

import org.springframework.stereotype.Service;

/**
 * EXHIBIT for Task 3. Do not fix: it stays broken so you can always run ./demo --scenario volatile.
 *
 * "Just make it volatile" is the most common wrong fix. volatile makes every read see the latest
 * write (visibility). It does not make read-add-write one step (atomicity).
 */
@Service
public class VolatileVisitCounter {

    private volatile int count;

    public void hit() {
        count++;                                  // still READ, ADD, WRITE. Another thread can get in between
    }

    public int count() {
        return count;
    }

    public void reset() {
        count = 0;
    }
}
