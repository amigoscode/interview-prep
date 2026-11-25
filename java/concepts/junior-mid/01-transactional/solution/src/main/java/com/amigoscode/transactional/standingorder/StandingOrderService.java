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

    /**
     * No try/catch in here. A failure has to leave this method as an exception, because that is the
     * only thing that tells the proxy to roll back. StandingOrderJob catches it, outside the transaction.
     */
    @Transactional
    public void execute(long fromId, long toId, long amount) {
        TransactionTrace.record("StandingOrderService.execute");

        Account from = accounts.require(fromId);
        from.withdraw(amount);
        accounts.save(from);

        Account to = accounts.require(toId);
        to.deposit(amount);
        accounts.save(to);
    }
}
