package com.amigoscode.ledger;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory ledger with thread-safe transfers.
 *
 * <p>Story 3 in three sentences: each account has its own lock, so unrelated transfers do not
 * queue behind one another; a transfer takes both locks in a global order (by account id), so
 * two threads moving money in opposite directions cannot each hold one lock and wait for the
 * other; and the balance check happens inside the locked section, so two threads cannot both
 * pass it and overdraw.
 */
public final class Ledger {

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final Map<String, TransferReceipt> receipts = new ConcurrentHashMap<>();

    public void open(String accountId, BigDecimal openingBalance) {
        requireId(accountId);
        if (openingBalance == null || openingBalance.signum() < 0) {
            throw new IllegalArgumentException("opening balance must not be negative");
        }
        if (accounts.putIfAbsent(accountId, new Account(accountId, openingBalance)) != null) {
            throw new IllegalArgumentException("account already exists: " + accountId);
        }
    }

    public BigDecimal balance(String accountId) {
        return find(accountId).balance();
    }

    public void deposit(String accountId, BigDecimal amount) {
        requirePositive(amount);
        Account account = find(accountId);
        account.lock.lock();
        try {
            account.credit(amount);
        } finally {
            account.lock.unlock();
        }
    }

    public void withdraw(String accountId, BigDecimal amount) {
        requirePositive(amount);
        Account account = find(accountId);
        account.lock.lock();
        try {
            if (account.balance().compareTo(amount) < 0) {
                throw new InsufficientFundsException(accountId);
            }
            account.debit(amount);
        } finally {
            account.lock.unlock();
        }
    }

    public void transfer(String fromId, String toId, BigDecimal amount) {
        requirePositive(amount);
        if (fromId.equals(toId)) {
            throw new IllegalArgumentException("cannot transfer to the same account");
        }
        Account from = find(fromId);
        Account to = find(toId);

        // Global lock order. Without this, A->B and B->A running together deadlock.
        Account first = from.id().compareTo(to.id()) < 0 ? from : to;
        Account second = first == from ? to : from;

        first.lock.lock();
        try {
            second.lock.lock();
            try {
                if (from.balance().compareTo(amount) < 0) { // check-then-act, inside both locks
                    throw new InsufficientFundsException(fromId);
                }
                from.debit(amount);
                to.credit(amount);
            } finally {
                second.lock.unlock();
            }
        } finally {
            first.lock.unlock();
        }
    }

    /**
     * Story 4, idempotency key. The first call with a key performs the transfer and records
     * the outcome; every later call with the same key returns that outcome without touching
     * balances. computeIfAbsent makes "check the key, then transfer" a single atomic step, so
     * two replays racing each other still move money once.
     */
    public TransferReceipt transfer(String idempotencyKey, String fromId, String toId, BigDecimal amount) {
        requireId(idempotencyKey);
        return receipts.computeIfAbsent(idempotencyKey, key -> {
            try {
                transfer(fromId, toId, amount);
                return TransferReceipt.success(key);
            } catch (InsufficientFundsException | AccountNotFoundException | IllegalArgumentException e) {
                return TransferReceipt.failure(key, e.getMessage());
            }
        });
    }

    private Account find(String accountId) {
        Account account = accounts.get(accountId);
        if (account == null) {
            throw new AccountNotFoundException(accountId);
        }
        return account;
    }

    private static void requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
