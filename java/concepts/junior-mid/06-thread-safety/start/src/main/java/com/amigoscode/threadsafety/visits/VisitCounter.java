package com.amigoscode.threadsafety.visits;

import org.springframework.stereotype.Service;

/**
 * Counts visits to the shop. Spring creates ONE VisitCounter and hands that same object to every
 * request thread, so the field below is shared by all of them.
 */
@Service
public class VisitCounter {

    // TODO Task 2: this field is shared by every request thread, and count++ is not one step.
    private int count;

    public void hit() {
        count++;                                  // READ count, ADD 1, WRITE count: three steps
    }

    public int count() {
        return count;
    }

    public void reset() {
        count = 0;
    }
}
