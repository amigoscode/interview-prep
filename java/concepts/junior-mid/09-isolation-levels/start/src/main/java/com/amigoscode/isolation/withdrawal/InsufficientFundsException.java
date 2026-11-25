package com.amigoscode.isolation.withdrawal;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(long accountId, long amount, long available) {
        super("Cannot withdraw " + amount + " from account " + accountId
                + ": the customer's accounts hold " + available + " in total");
    }
}
