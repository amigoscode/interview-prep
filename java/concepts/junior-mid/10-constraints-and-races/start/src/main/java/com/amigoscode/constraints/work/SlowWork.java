package com.amigoscode.constraints.work;

/**
 * Stands in for the work a real application does between checking and inserting: hashing the
 * password, charging the card, loading the defaults. It takes time, and that time is the window in
 * which a second request can run the same check and get the same answer.
 */
public interface SlowWork {

    void run(String step);
}
