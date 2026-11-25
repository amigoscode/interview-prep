package com.amigoscode.isolation.withdrawal;

/** Every attempt was cancelled by the database. Nothing was withdrawn, and it is safe to try again. */
public class WithdrawalConflictException extends RuntimeException {

    public WithdrawalConflictException(long accountId, int attempts, Throwable cause) {
        super("Withdrawal from account " + accountId + " was cancelled " + attempts
                + " times by concurrent transactions. Nothing was withdrawn. Try again.", cause);
    }
}
