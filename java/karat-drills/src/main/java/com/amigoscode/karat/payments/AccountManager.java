package com.amigoscode.karat.payments;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * We are developing a payment transaction monitoring system that tracks accounts and their
 * transactions. An {@link Account} has a unique ID and an owner name; a {@link Transaction} moves
 * money in (CREDIT) or out (DEBIT) of an account. This class manages accounts and transactions and
 * computes each account's current balance.
 *
 * <p>Part 1: {@code AccountManagerTest} is not passing because of a bug in this class. Read the
 * code, run the test, fix the bug.
 *
 * <p>Part 2: we are adding basic analytics over transactions. Implement
 * {@link #getAverageTransactionAmountByAccount}.
 */
public class AccountManager {

    public final Map<Integer, Account> accounts = new HashMap<>();
    public final List<Transaction> transactions = new ArrayList<>();

    public void addAccount(Account account) {
        accounts.put(account.accountId, account);
    }

    /** Input transactions always refer to valid accounts for this question. */
    public void addTransaction(Transaction tx) {
        transactions.add(tx);
    }

    /** Returns the current balance for the given account: credits minus debits. */
    public double getBalance(int accountId) {
        double balance = 0.0;
        for (Transaction tx : transactions) {
            if (tx.accountId == accountId) {
                // Bug was ignoring DEBIT transactions: only credits were added, debits never subtracted.
                if (tx.type == TransactionType.CREDIT) {
                    balance += tx.amount;
                } else {
                    balance -= tx.amount;
                }
            }
        }
        return balance;
    }

    /**
     * Part 2: the average transaction amount for each account, as a map from account ID to
     * average.
     *
     * <ol>
     *   <li>Both CREDIT and DEBIT transactions count.
     *   <li>Amounts are treated as absolute values when averaging.
     *   <li>Accounts with no transactions do not appear in the result.
     *   <li>Transactions always refer to valid accounts.
     * </ol>
     */
    public Map<Integer, Double> getAverageTransactionAmountByAccount() {
        Map<Integer, Double> totals = new LinkedHashMap<>();
        Map<Integer, Integer> counts = new HashMap<>();
        for (Transaction tx : transactions) {
            totals.merge(tx.accountId, Math.abs(tx.amount), Double::sum);
            counts.merge(tx.accountId, 1, Integer::sum);
        }
        Map<Integer, Double> averages = new LinkedHashMap<>();
        for (Map.Entry<Integer, Double> entry : totals.entrySet()) {
            averages.put(entry.getKey(), entry.getValue() / counts.get(entry.getKey()));
        }
        return averages;
    }
}
