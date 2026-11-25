package com.amigoscode.isolation.withdrawal;

import com.amigoscode.isolation.account.Account;
import com.amigoscode.isolation.account.AccountNotFoundException;
import com.amigoscode.isolation.account.AccountRepository;
import com.amigoscode.isolation.checkpoint.Checkpoint;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
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
     * stay at zero or above. So the decision is based on rows this transaction READS but does not
     * WRITE. Only SERIALIZABLE makes the database track that: if another transaction changed what
     * this one read, one of the two is cancelled with SQLSTATE 40001, and WithdrawalService runs it
     * again.
     *
     * @return the new balance of the account
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
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
