package com.amigoscode.transactional.trap;

import com.amigoscode.transactional.trace.TransactionTrace;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * An exhibit for Task 7: the same in start/ and solution/, broken on purpose. Do not fix it.
 * Every method involved is @Transactional and every call goes through a proxy, and the money
 * still goes missing, because the debit and the credit are two separate transactions.
 */
@Service
public class SplitTransferService {

    private final Ledger ledger;

    public SplitTransferService(Ledger ledger) {
        this.ledger = ledger;
    }

    @Transactional
    public void transfer(long fromId, long toId, long amount) {
        TransactionTrace.record("SplitTransferService.transfer");
        ledger.debit(fromId, amount);     // REQUIRES_NEW: committed as soon as it returns
        ledger.credit(toId, amount);      // fails when the receiver is frozen. Too late for the debit.
    }
}
