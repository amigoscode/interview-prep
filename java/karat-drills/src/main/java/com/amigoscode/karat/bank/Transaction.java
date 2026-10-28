package com.amigoscode.karat.bank;

import java.util.Objects;

/**
 * A single financial event. The amount is always positive; the type says which way the money
 * moves. The timestamp is a day number since a reference point and the source says where the
 * transaction was imported from (a partner institution, a statement upload, a legacy migration).
 */
public class Transaction {
    public final String transactionId;
    public final int accountId;
    public final double amount;
    public final TransactionType transactionType;
    public final int timestamp;
    public final String description;
    public final String source;

    public Transaction(
            String transactionId,
            int accountId,
            double amount,
            TransactionType transactionType,
            int timestamp,
            String description,
            String source) {
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.amount = amount;
        this.transactionType = transactionType;
        this.timestamp = timestamp;
        this.description = description;
        this.source = source;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Transaction t)) {
            return false;
        }
        return transactionId.equals(t.transactionId)
                && accountId == t.accountId
                && Double.compare(amount, t.amount) == 0
                && transactionType == t.transactionType
                && timestamp == t.timestamp
                && description.equals(t.description)
                && source.equals(t.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactionId, accountId, amount, transactionType, timestamp, description, source);
    }

    @Override
    public String toString() {
        return "Transaction ID: " + transactionId
                + ", Account: " + accountId
                + ", Amount: " + amount
                + ", Type: " + transactionType
                + ", Timestamp: " + timestamp
                + ", Description: " + description
                + ", Source: " + source;
    }
}
