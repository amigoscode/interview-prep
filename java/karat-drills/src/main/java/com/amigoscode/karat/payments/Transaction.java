package com.amigoscode.karat.payments;

/** Money moving in or out of an account. The amount is always positive in inputs. */
public class Transaction {
    public final int transactionId;
    public final int accountId;
    public final TransactionType type;
    public final double amount;
    public final long timestampSec; // Unix-style seconds (monotonic for tests)

    public Transaction(int transactionId, int accountId, TransactionType type, double amount,
                       long timestampSec) {
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.type = type;
        this.amount = amount;
        this.timestampSec = timestampSec;
    }
}
