package com.amigoscode.karat.bank;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * We are building a program to manage a bank's imported transactions. The system tracks
 * transactions from multiple sources (partner institutions, customer statement uploads, legacy
 * migrations), each belonging to an account. Staff can import transactions, query per-account
 * summaries and reconcile activity across sources. DEPOSIT and INTEREST are credits and increase
 * the balance; WITHDRAWAL, FEE and TRANSFER are debits and decrease it.
 *
 * <p>Part 1: {@code BankImportTest} is not passing because of a bug in this class. Read the code,
 * run the test, fix the bug.
 *
 * <p>Part 2: as transactions are imported the system must keep each account's running balance in
 * {@link #balances}, and report activity broken down by transaction type. Change
 * {@link #addTransaction} and implement {@link #getTypeSummary}.
 *
 * <p>Amounts are doubles because the provided tests use them; in production money would be long
 * cents or BigDecimal so that sums are exact.
 */
public class ImportSystem {

    public final List<Transaction> transactions = new ArrayList<>();

    /**
     * Part 2.1: the running balance of each account, keyed by account ID. Initially empty; a new
     * account starts at 0 before its first transaction. Kept up to date by {@link #addTransaction}.
     */
    public final Map<Integer, Double> balances = new HashMap<>();

    /**
     * Adds a transaction to the system.
     *
     * <p>Part 2.2: besides appending the transaction, update the account's balance in
     * {@link #balances} based on the transaction's direction: credits (DEPOSIT, INTEREST) add the
     * amount, debits (WITHDRAWAL, FEE, TRANSFER) subtract it. If the account is new (not yet in
     * {@link #balances}) initialize it to 0 before applying the change.
     */
    public void addTransaction(Transaction transaction) {
        transactions.add(transaction);
        balances.merge(transaction.accountId, signedAmount(transaction), Double::sum);
    }

    /**
     * Calculates and returns summary statistics for a given account: "total_credits",
     * "total_debits" and "net_change" (credits minus debits) as doubles, and "transaction_count"
     * as an int.
     */
    public Map<String, Object> getAccountSummary(int accountId) {
        double totalCredits = 0;
        double totalDebits = 0;
        int transactionCount = 0;
        for (Transaction t : transactions) {
            if (t.accountId != accountId) {
                continue;
            }
            transactionCount++;
            if (isCredit(t.transactionType)) {
                totalCredits += t.amount;
            } else {
                totalDebits += t.amount;
            }
        }
        double netChange = totalCredits - totalDebits;

        Map<String, Object> result = new HashMap<>();
        result.put("total_credits", totalCredits);
        result.put("total_debits", totalDebits);
        result.put("net_change", netChange);
        result.put("transaction_count", transactionCount);
        return result;
    }

    /**
     * Part 2.3: a map from transaction type name (a string, uppercase, matching the enum name
     * exactly, for example "DEPOSIT") to an inner map with keys "count" (an Integer) and
     * "total_amount" (a Double), aggregated across all accounts. Only types that have at least one
     * transaction are included; types with none are omitted.
     */
    public Map<String, Map<String, Object>> getTypeSummary() {
        Map<String, Integer> countByType = new LinkedHashMap<>();
        Map<String, Double> totalByType = new LinkedHashMap<>();
        for (Transaction t : transactions) {
            String type = t.transactionType.name();
            countByType.merge(type, 1, Integer::sum);
            totalByType.merge(type, t.amount, Double::sum);
        }
        Map<String, Map<String, Object>> summary = new LinkedHashMap<>();
        for (String type : countByType.keySet()) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("count", countByType.get(type));
            entry.put("total_amount", totalByType.get(type));
            summary.put(type, entry);
        }
        return summary;
    }

    /** The amount as a signed movement of the balance: positive for a credit, negative for a debit. */
    private static double signedAmount(Transaction transaction) {
        return isCredit(transaction.transactionType) ? transaction.amount : -transaction.amount;
    }

    private static boolean isCredit(TransactionType type) {
        // Bug was TRANSFER counted as a credit: a transfer is money leaving the account, a debit.
        return type == TransactionType.DEPOSIT || type == TransactionType.INTEREST;
    }
}
