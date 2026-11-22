package com.amigoscode.transactional.transfer;

import com.amigoscode.transactional.account.Account;
import com.amigoscode.transactional.account.AccountRepository;
import com.amigoscode.transactional.trace.TransactionTrace;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Moves money from one account to another. Both balances must change, or neither. */
@Service
public class TransferService {

    private final AccountRepository accounts;

    public TransferService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    /** The method the rest of the application calls. */
    // TODO Task 2: a failed transfer loses money, even though moveMoney below "is @Transactional". Why? Fix it.
    public void transfer(long fromId, long toId, long amount) {
        TransactionTrace.record("TransferService.transfer");
        if (fromId == toId) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }
        moveMoney(fromId, toId, amount);
    }

    /** Debits one account and credits the other, in one transaction. */
    @Transactional
    public void moveMoney(long fromId, long toId, long amount) {
        TransactionTrace.record("TransferService.moveMoney");

        Account from = accounts.require(fromId);
        from.withdraw(amount);
        accounts.save(from);

        Account to = accounts.require(toId);
        to.deposit(amount);          // fails here when the receiver is frozen
        accounts.save(to);
    }
}
