package com.amigoscode.transactional.account;

import com.amigoscode.transactional.trace.TransactionTrace;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account lookups and admin changes. The class is read-only by default, which is right for the
 * lookups. A method that writes has to say so.
 */
@Service
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accounts;

    public AccountService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    public long balanceOf(long accountId) {
        TransactionTrace.record("AccountService.balanceOf");
        return accounts.require(accountId).getBalance();
    }

    @Transactional
    public void rename(long accountId, String newOwner) {
        TransactionTrace.record("AccountService.rename");
        Account account = accounts.require(accountId);
        account.rename(newOwner);
        accounts.save(account);
    }
}
