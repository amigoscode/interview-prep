package com.amigoscode.transactional.trap;

import com.amigoscode.transactional.account.Account;
import com.amigoscode.transactional.account.AccountRepository;
import com.amigoscode.transactional.trace.TransactionTrace;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Part of the Task 7 exhibit. Each method commits on its own, whatever the caller does. Do not fix it. */
@Service
public class Ledger {

    private final AccountRepository accounts;

    public Ledger(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void debit(long accountId, long amount) {
        TransactionTrace.record("Ledger.debit");
        Account account = accounts.require(accountId);
        account.withdraw(amount);
        accounts.save(account);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void credit(long accountId, long amount) {
        TransactionTrace.record("Ledger.credit");
        Account account = accounts.require(accountId);
        account.deposit(amount);
        accounts.save(account);
    }
}
