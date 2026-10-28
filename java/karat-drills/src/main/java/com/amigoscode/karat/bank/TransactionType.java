package com.amigoscode.karat.bank;

/**
 * The category of a financial event. DEPOSIT and INTEREST are credits (they increase the account
 * balance). WITHDRAWAL, FEE and TRANSFER are debits (they decrease the account balance).
 */
public enum TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER,
    FEE,
    INTEREST
}
