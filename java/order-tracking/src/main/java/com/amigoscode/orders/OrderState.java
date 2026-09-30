package com.amigoscode.orders;

import java.util.EnumSet;
import java.util.Set;

/**
 * The life of one order: RECEIVED, ACCEPTED, PICKED_UP, DELIVERED, with CANCELLED allowed only
 * before the rider has the food.
 */
public enum OrderState {
    RECEIVED, ACCEPTED, PICKED_UP, DELIVERED, CANCELLED;

    /** The states one legal step away. Terminal states have none. */
    public Set<OrderState> next() {
        return switch (this) {
            case RECEIVED -> EnumSet.of(ACCEPTED, CANCELLED);
            case ACCEPTED -> EnumSet.of(PICKED_UP, CANCELLED);
            case PICKED_UP -> EnumSet.of(DELIVERED);
            case DELIVERED, CANCELLED -> EnumSet.noneOf(OrderState.class);
        };
    }

    public boolean canTransitionTo(OrderState target) {
        return next().contains(target);
    }

    /**
     * True when {@code target} is one or more legal steps away. Used for gaps in the stream:
     * PICKED_UP may follow RECEIVED if the ACCEPTED event is still in flight.
     */
    public boolean canReach(OrderState target) {
        for (OrderState step : next()) {
            if (step == target || step.canReach(target)) return true;
        }
        return false;
    }

    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED;
    }
}
