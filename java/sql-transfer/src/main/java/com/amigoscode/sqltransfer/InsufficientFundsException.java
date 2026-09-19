package com.amigoscode.sqltransfer;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String accountId) {
        super("insufficient funds in " + accountId);
    }
}
