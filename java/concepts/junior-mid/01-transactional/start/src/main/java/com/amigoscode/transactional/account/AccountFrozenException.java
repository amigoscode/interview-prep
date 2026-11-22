package com.amigoscode.transactional.account;

/** Unchecked (it extends RuntimeException). */
public class AccountFrozenException extends RuntimeException {

    public AccountFrozenException(long id, String owner) {
        super("Account " + id + " (" + owner + ") is frozen");
    }
}
