package com.amigoscode.idempotency.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "customer")
public class Customer {

    @Id
    private Long id;

    private String name;

    @Column(name = "balance_cents")
    private long balanceCents;

    protected Customer() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getBalanceCents() {
        return balanceCents;
    }
}
