package com.amigoscode.ledger;

import java.math.BigDecimal;
import java.util.concurrent.locks.ReentrantLock;

/**
 * One row of the ledger. The balance is guarded by {@link #lock}; callers of the mutating
 * methods must hold it. Keeping the lock on the account, not on the ledger, is what lets
 * transfers on unrelated accounts run in parallel.
 */
final class Account {

    private final String id;
    private BigDecimal balance;
    final ReentrantLock lock = new ReentrantLock();

    Account(String id, BigDecimal openingBalance) {
        this.id = id;
        this.balance = openingBalance;
    }

    String id() {
        return id;
    }

    /** Safe to call from anywhere: takes the lock, and ReentrantLock allows re-entry. */
    BigDecimal balance() {
        lock.lock();
        try {
            return balance;
        } finally {
            lock.unlock();
        }
    }

    // The two below assume the caller already holds lock; they are the critical section.

    void credit(BigDecimal amount) {
        assert lock.isHeldByCurrentThread();
        balance = balance.add(amount);
    }

    void debit(BigDecimal amount) {
        assert lock.isHeldByCurrentThread();
        balance = balance.subtract(amount);
    }
}
