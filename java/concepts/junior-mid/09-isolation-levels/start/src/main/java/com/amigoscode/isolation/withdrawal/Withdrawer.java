package com.amigoscode.isolation.withdrawal;

import com.amigoscode.isolation.account.Account;
import com.amigoscode.isolation.account.AccountNotFoundException;
import com.amigoscode.isolation.account.AccountRepository;
import com.amigoscode.isolation.checkpoint.Checkpoint;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One database transaction per call: one attempt at a withdrawal. */
@Service
public class Withdrawer {

    private final AccountRepository accounts;
    private final Checkpoint checkpoint;

    public Withdrawer(AccountRepository accounts, Checkpoint checkpoint) {
        this.accounts = accounts;
        this.checkpoint = checkpoint;
    }

    /**
     * The rule: one account may go below zero, as long as all of the customer's accounts together
     * stay at zero or above.
     *
     * <p>THE BUG IS HERE, twice. Between the READ and the WRITE another transaction can commit, and
     * at the default isolation level this one carries on with numbers that are no longer true.
     *
     * <p>TODO Task 4: two withdrawals from the SAME account lose an update. Pick the isolation level
     * that makes Postgres cancel the second writer, then add the retry in WithdrawalService.
     *
     * <p>TODO Task 6: two withdrawals from DIFFERENT accounts break the rule (write skew). Pick the
     * isolation level that stops that too.
     *
     * @return the new balance of the account
     */
    @Transactional
    public long withdraw(long accountId, long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive, got " + amount);
        }
        Account account = accounts.findById(accountId)                        // READ this account
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        long available = accounts.totalBalance(account.getOwner());           // READ all the customer's accounts
        if (available < amount) {                                             // CHECK the rule
            throw new InsufficientFundsException(accountId, amount, available);
        }
        checkpoint.reached(Checkpoint.FUNDS_CHECKED);                         // ...time passes...
        account.setBalance(account.getBalance() - amount);                    // WRITE a value computed from the READ
        return account.getBalance();
    }
}
