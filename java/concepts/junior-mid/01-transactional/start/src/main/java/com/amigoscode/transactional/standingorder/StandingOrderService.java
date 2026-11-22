package com.amigoscode.transactional.standingorder;

import com.amigoscode.transactional.account.Account;
import com.amigoscode.transactional.account.AccountRepository;
import com.amigoscode.transactional.trace.TransactionTrace;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Executes one standing order: a payment the customer set up in advance. */
@Service
public class StandingOrderService {

    private final AccountRepository accounts;

    public StandingOrderService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    /** Returns true when the payment went through, false when it failed. The nightly job must not crash. */
    // TODO Task 4: a failed standing order returns false, and the sender has lost the money anyway.
    //  What does the proxy see when this method ends? You may change this method's signature.
    @Transactional
    public boolean execute(long fromId, long toId, long amount) {
        TransactionTrace.record("StandingOrderService.execute");
        try {
            Account from = accounts.require(fromId);
            from.withdraw(amount);
            accounts.save(from);

            Account to = accounts.require(toId);
            to.deposit(amount);
            accounts.save(to);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
