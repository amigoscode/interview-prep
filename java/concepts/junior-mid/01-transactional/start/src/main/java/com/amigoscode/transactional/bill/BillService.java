package com.amigoscode.transactional.bill;

import com.amigoscode.transactional.account.Account;
import com.amigoscode.transactional.account.AccountRepository;
import com.amigoscode.transactional.trace.TransactionTrace;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Pays a bill: takes the money from the payer and gives it to the biller. */
@Service
public class BillService {

    private final AccountRepository accounts;

    public BillService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    // TODO Task 4: when the bill is rejected the payer still loses the money. The method is transactional
    //  and the call goes through the proxy this time. Look at what kind of exception is thrown.
    @Transactional
    public void payBill(long payerId, long billerId, long amount) throws BillRejectedException {
        TransactionTrace.record("BillService.payBill");

        Account payer = accounts.require(payerId);
        payer.withdraw(amount);
        accounts.save(payer);

        Account biller = accounts.require(billerId);
        if (biller.isFrozen()) {
            throw new BillRejectedException("Biller " + biller.getOwner() + " cannot receive payments");
        }
        biller.deposit(amount);
        accounts.save(biller);
    }
}
