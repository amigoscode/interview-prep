package com.amigoscode.isolation.checkpoint;

/**
 * A named point in the middle of a transaction, between a READ and what the code does with it.
 * In production nothing happens here. The demo and the tests use it to let a second session do
 * its work at exactly this moment, so the dangerous interleaving happens every time instead of
 * once a month.
 */
public interface Checkpoint {

    /** StatementService has read the lines and is about to read the total. */
    String LINES_READ = "statement: lines read";

    /** Withdrawer has checked the funds and is about to write the new balance. */
    String FUNDS_CHECKED = "withdrawal: funds checked";

    void reached(String name);
}
