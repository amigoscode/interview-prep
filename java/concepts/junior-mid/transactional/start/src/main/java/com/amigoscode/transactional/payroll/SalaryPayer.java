package com.amigoscode.transactional.payroll;

import com.amigoscode.transactional.account.Account;
import com.amigoscode.transactional.account.AccountRepository;
import com.amigoscode.transactional.trace.TransactionTrace;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalaryPayer {

    private final AccountRepository accounts;

    public SalaryPayer(AccountRepository accounts) {
        this.accounts = accounts;
    }

    /** Default propagation, REQUIRED: join the caller's transaction if there is one. */
    @Transactional
    public void pay(long employerId, long employeeId, long salary) {
        TransactionTrace.record("SalaryPayer.pay");

        Account employer = accounts.require(employerId);
        employer.withdraw(salary);
        accounts.save(employer);

        Account employee = accounts.require(employeeId);
        employee.deposit(salary);
        accounts.save(employee);
    }
}
