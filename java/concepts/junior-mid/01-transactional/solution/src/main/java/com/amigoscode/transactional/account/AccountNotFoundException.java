package com.amigoscode.transactional.account;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(long id) {
        super("Account " + id + " does not exist");
    }
}
