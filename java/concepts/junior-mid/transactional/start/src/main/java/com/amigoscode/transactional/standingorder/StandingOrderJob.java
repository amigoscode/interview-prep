package com.amigoscode.transactional.standingorder;

import org.springframework.stereotype.Component;

/**
 * The nightly job. It must never crash: one failed standing order must not stop the next one.
 * It is NOT transactional. Each call to StandingOrderService.execute is its own transaction.
 */
@Component
public class StandingOrderJob {

    private final StandingOrderService standingOrders;

    public StandingOrderJob(StandingOrderService standingOrders) {
        this.standingOrders = standingOrders;
    }

    /** Returns true when the payment went through, false when it failed. Never throws. */
    // TODO Task 4: this contract stays (true or false, never an exception). How it is met may change.
    public boolean run(long fromId, long toId, long amount) {
        return standingOrders.execute(fromId, toId, amount);
    }
}
