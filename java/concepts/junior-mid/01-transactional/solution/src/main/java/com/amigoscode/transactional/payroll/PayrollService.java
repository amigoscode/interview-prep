package com.amigoscode.transactional.payroll;

import com.amigoscode.transactional.account.AccountFrozenException;
import com.amigoscode.transactional.trace.TransactionTrace;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pays a salary to every employee. This class is an exhibit for Task 5: it is the same in start/
 * and solution/, and it stays surprising on purpose. Do not fix it.
 */
@Service
public class PayrollService {

    private final SalaryPayer salaryPayer;

    public PayrollService(SalaryPayer salaryPayer) {
        this.salaryPayer = salaryPayer;
    }

    /** Returns how many employees were paid. One frozen account "must not stop the others". */
    @Transactional
    public int payEveryone(long employerId, List<Long> employeeIds, long salary) {
        TransactionTrace.record("PayrollService.payEveryone");
        int paid = 0;
        for (long employeeId : employeeIds) {
            try {
                salaryPayer.pay(employerId, employeeId, salary);
                paid++;
            } catch (AccountFrozenException e) {
                // Skip this employee and carry on with the next one. Or so the author hoped.
            }
        }
        return paid;
    }
}
