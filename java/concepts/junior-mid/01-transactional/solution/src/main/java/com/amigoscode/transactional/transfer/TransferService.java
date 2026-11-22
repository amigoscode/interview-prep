package com.amigoscode.transactional.transfer;

import com.amigoscode.transactional.account.Account;
import com.amigoscode.transactional.account.AccountRepository;
import com.amigoscode.transactional.trace.TransactionTrace;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Moves money from one account to another. Both balances change, or neither does. */
@Service
public class TransferService {

    private final AccountRepository accounts;

    public TransferService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    /**
     * The method the rest of the application calls. It is the entry point, so the transaction
     * starts here: this call comes from another bean and therefore goes through the proxy.
     */
    @Transactional
    public void transfer(long fromId, long toId, long amount) {
        TransactionTrace.record("TransferService.transfer");
        if (fromId == toId) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }
        moveMoney(fromId, toId, amount);
    }

    /** Called from transfer() above, as this.moveMoney(...). It joins the transaction transfer() opened. */
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
