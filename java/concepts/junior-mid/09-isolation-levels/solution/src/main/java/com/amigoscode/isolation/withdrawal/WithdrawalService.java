package com.amigoscode.isolation.withdrawal;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;

/**
 * Deliberately NOT transactional. When Postgres cancels a transaction with SQLSTATE 40001 the whole
 * transaction is gone, so the only way to try again is a brand new transaction that reads
 * everything again. The loop therefore lives outside the transactional bean.
 */
@Service
public class WithdrawalService {

    public static final int MAX_ATTEMPTS = 3;

    private final Withdrawer withdrawer;

    public WithdrawalService(Withdrawer withdrawer) {
        this.withdrawer = withdrawer;
    }

    public long withdraw(long accountId, long amount) {
        for (int attempt = 1; ; attempt++) {
            try {
                return withdrawer.withdraw(accountId, amount);
            } catch (ConcurrencyFailureException e) {
                // SQLSTATE 40001 arrives as CannotAcquireLockException, a ConcurrencyFailureException.
                // The transaction is already rolled back, so the next attempt re-reads the balances.
                if (attempt == MAX_ATTEMPTS) {
                    throw new WithdrawalConflictException(accountId, attempt, e);
                }
            }
        }
    }
}
