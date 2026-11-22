package com.amigoscode.transactional.atm;

import com.amigoscode.transactional.account.Account;
import com.amigoscode.transactional.account.AccountRepository;
import com.amigoscode.transactional.audit.AuditService;
import com.amigoscode.transactional.trace.TransactionTrace;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cash withdrawals. Every attempt is written to the audit log first, then the money is taken. */
@Service
public class AtmService {

    private final AccountRepository accounts;
    private final AuditService audit;

    public AtmService(AccountRepository accounts, AuditService audit) {
        this.accounts = accounts;
        this.audit = audit;
    }

    @Transactional
    public void withdraw(long accountId, long amount) {
        TransactionTrace.record("AtmService.withdraw");
        audit.record("ATM_WITHDRAWAL_ATTEMPT", "account " + accountId + ", amount " + amount);

        Account account = accounts.require(accountId);
        account.withdraw(amount);    // fails here when there is not enough money
        accounts.save(account);
    }
}
