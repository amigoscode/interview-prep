package com.amigoscode.isolation.account;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(long accountId) {
        super("Account " + accountId + " does not exist");
    }
}
