package com.amigoscode.ledger;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String accountId) {
        super("no such account: " + accountId);
    }
}
