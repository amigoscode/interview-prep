package com.amigoscode.constraints.user;

public class EmailTakenException extends RuntimeException {

    public EmailTakenException(String email) {
        super("An account with the email " + email + " already exists");
    }
}
