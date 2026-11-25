package com.amigoscode.isolation.account;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "account")
public class Account {

    @Id
    private Long id;

    private String owner;

    private String name;

    private long balance;

    protected Account() {
    }

    public Account(long id, String owner, String name, long balance) {
        this.id = id;
        this.owner = owner;
        this.name = name;
        this.balance = balance;
    }

    public Long getId() {
        return id;
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public long getBalance() {
        return balance;
    }

    public void setBalance(long balance) {
        this.balance = balance;
    }
}
