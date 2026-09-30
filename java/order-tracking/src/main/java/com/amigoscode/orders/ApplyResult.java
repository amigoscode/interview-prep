package com.amigoscode.orders;

/**
 * What {@link OrderTracker#apply} did with an event. An illegal transition is normal data on a
 * stream, not a bug, so it is a value the consumer can log or dead-letter, not an exception that
 * would kill the consumer loop.
 */
public sealed interface ApplyResult {

    /** The event moved the order to {@code state}. */
    record Applied(OrderState state) implements ApplyResult {}

    /** Same eventId seen before for this order: redelivery, nothing changed. */
    record Duplicate() implements ApplyResult {}

    /** Sequence is not newer than the one already applied: an old message arriving late. */
    record Stale(long currentSequence) implements ApplyResult {}

    /** The state machine does not allow this move. */
    record Rejected(OrderState current, OrderState attempted) implements ApplyResult {}
}
