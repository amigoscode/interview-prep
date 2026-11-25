package com.amigoscode.constraints.subscription;

public class SubscriptionNotFoundException extends RuntimeException {

    public SubscriptionNotFoundException(long subscriptionId) {
        super("Subscription " + subscriptionId + " does not exist");
    }
}
