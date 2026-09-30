package com.amigoscode.orders;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Mutable state of one order. Every method is synchronized on the order itself, so events for
 * different orders never contend, and events for the same order are applied one at a time.
 */
final class Order {

    private final OrderDetails details;
    private final Set<String> seenEventIds = new HashSet<>();
    private OrderState state;          // null until the first event is applied
    private long sequence;             // 0 until the first event is applied
    private Instant deliveredAt;

    Order(OrderDetails details) {
        this.details = details;
    }

    OrderDetails details() {
        return details;
    }

    synchronized ApplyResult apply(OrderEvent event) {
        if (!seenEventIds.add(event.eventId())) return new ApplyResult.Duplicate();
        if (event.sequence() <= sequence) return new ApplyResult.Stale(sequence);
        // Accept-the-latest: a gap in the sequence is fine as long as the target is reachable.
        if (state != null && !state.canReach(event.state())) {
            return new ApplyResult.Rejected(state, event.state());
        }
        state = event.state();
        sequence = event.sequence();
        if (state == OrderState.DELIVERED) deliveredAt = event.occurredAt();
        return new ApplyResult.Applied(state);
    }

    synchronized Optional<OrderState> state() {
        return Optional.ofNullable(state);
    }

    synchronized boolean isLateAt(Instant now) {
        return state != null && !state.isTerminal() && now.isAfter(details.promisedBy());
    }

    /** When this order was delivered after its promised time, the delivery instant. */
    synchronized Optional<Instant> lateDeliveryAt() {
        if (deliveredAt == null || !deliveredAt.isAfter(details.promisedBy())) return Optional.empty();
        return Optional.of(deliveredAt);
    }
}
