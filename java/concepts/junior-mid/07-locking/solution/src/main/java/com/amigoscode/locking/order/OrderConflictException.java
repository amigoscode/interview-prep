package com.amigoscode.locking.order;

/** The order kept losing the race for the row and gave up. The buyer can safely try again. */
public class OrderConflictException extends RuntimeException {

    public OrderConflictException(long productId, int attempts) {
        super("Product " + productId + " was changed by other buyers " + attempts
                + " times in a row. Please try again");
    }
}
