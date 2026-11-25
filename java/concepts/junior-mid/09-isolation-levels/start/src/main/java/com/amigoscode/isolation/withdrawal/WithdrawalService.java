package com.amigoscode.isolation.withdrawal;

import org.springframework.stereotype.Service;

/**
 * Deliberately NOT transactional: each Withdrawer.withdraw call is its own transaction, and this
 * class decides what to do when one of them is cancelled by the database.
 */
@Service
public class WithdrawalService {

    public static final int MAX_ATTEMPTS = 3;

    private final Withdrawer withdrawer;

    public WithdrawalService(Withdrawer withdrawer) {
        this.withdrawer = withdrawer;
    }

    public long withdraw(long accountId, long amount) {
        // TODO Task 4: when Postgres cancels the transaction with a serialization failure (SQLSTATE
        //      40001), try again, up to MAX_ATTEMPTS attempts in total. If every attempt is
        //      cancelled, throw WithdrawalConflictException.
        return withdrawer.withdraw(accountId, amount);
    }
}
