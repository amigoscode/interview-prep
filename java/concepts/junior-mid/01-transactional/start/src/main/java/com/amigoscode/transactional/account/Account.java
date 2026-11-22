package com.amigoscode.transactional.account;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A bank account. The balance is in whole pounds to keep the numbers easy to read. */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    private Long id;

    private String owner;

    private long balance;

    private boolean frozen;

    protected Account() {
    }

    public Account(long id, String owner, long balance, boolean frozen) {
        this.id = id;
        this.owner = owner;
        this.balance = balance;
        this.frozen = frozen;
    }

    /** Takes money out. Fails when the account is frozen or does not hold enough. */
    public void withdraw(long amount) {
        requirePositive(amount);
        requireNotFrozen();
        if (amount > balance) {
            throw new InsufficientFundsException(id, balance, amount);
        }
        balance -= amount;
    }

    /** Puts money in. Fails when the account is frozen. */
    public void deposit(long amount) {
        requirePositive(amount);
        requireNotFrozen();
        balance += amount;
    }

    public void rename(String newOwner) {
        if (newOwner == null || newOwner.isBlank()) {
            throw new IllegalArgumentException("The owner must not be blank");
        }
        this.owner = newOwner;
    }

    private void requireNotFrozen() {
        if (frozen) {
            throw new AccountFrozenException(id, owner);
        }
    }

    private static void requirePositive(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("The amount must be positive, got: " + amount);
        }
    }

    public Long getId() {
        return id;
    }

    public String getOwner() {
        return owner;
    }

    public long getBalance() {
        return balance;
    }

    public boolean isFrozen() {
        return frozen;
    }
}
