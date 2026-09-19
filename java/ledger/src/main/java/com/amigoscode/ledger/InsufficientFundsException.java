package com.amigoscode.ledger;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String accountId) {
        super("insufficient funds in " + accountId);
    }
}
