package com.amigoscode.constraints.user;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(long userId) {
        super("User " + userId + " does not exist");
    }
}
