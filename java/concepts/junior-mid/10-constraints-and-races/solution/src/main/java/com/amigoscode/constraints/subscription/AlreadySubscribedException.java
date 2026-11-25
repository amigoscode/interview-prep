package com.amigoscode.constraints.subscription;

public class AlreadySubscribedException extends RuntimeException {

    public AlreadySubscribedException(long userId) {
        super("User " + userId + " already has an active subscription");
    }
}
