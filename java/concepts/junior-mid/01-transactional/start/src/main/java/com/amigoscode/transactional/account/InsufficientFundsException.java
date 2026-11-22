package com.amigoscode.transactional.account;

/** Unchecked (it extends RuntimeException). */
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(long id, long balance, long amount) {
        super("Account " + id + " holds " + balance + ", cannot take out " + amount);
    }
}
