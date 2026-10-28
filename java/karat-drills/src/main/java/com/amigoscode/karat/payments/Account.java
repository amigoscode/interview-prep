package com.amigoscode.karat.payments;

/** An account has a unique ID and an owner name. */
public class Account {
    public final int accountId;
    public final String ownerName;

    public Account(int accountId, String ownerName) {
        this.accountId = accountId;
        this.ownerName = ownerName;
    }
}
