package com.amigoscode.transactional.fee;

import com.amigoscode.transactional.account.Account;
import com.amigoscode.transactional.account.AccountRepository;
import com.amigoscode.transactional.trace.TransactionTrace;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Charges the monthly account fee. */
@Service
public class FeeService {

    public static final long MONTHLY_FEE = 5;

    private final AccountRepository accounts;

    public FeeService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    // TODO Task 8: this method crashes with a NullPointerException on a field the constructor sets. Find out why.
    @Transactional
    public final void chargeMonthlyFee(long accountId) {
        TransactionTrace.record("FeeService.chargeMonthlyFee");
        Account account = accounts.require(accountId);
        account.withdraw(MONTHLY_FEE);
        accounts.save(account);
    }
}
