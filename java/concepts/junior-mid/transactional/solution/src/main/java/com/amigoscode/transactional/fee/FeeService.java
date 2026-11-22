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

    /** Not final: the proxy is a subclass of FeeService, and a subclass cannot override a final method. */
    @Transactional
    public void chargeMonthlyFee(long accountId) {
        TransactionTrace.record("FeeService.chargeMonthlyFee");
        Account account = accounts.require(accountId);
        account.withdraw(MONTHLY_FEE);
        accounts.save(account);
    }
}
