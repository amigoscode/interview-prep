package com.amigoscode.idempotency.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "payment")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id")
    private long customerId;

    @Column(name = "amount_cents")
    private long amountCents;

    @Column(name = "created_at")
    private Instant createdAt;

    protected Payment() {
    }

    public Payment(long customerId, long amountCents) {
        this.customerId = customerId;
        this.amountCents = amountCents;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public long getCustomerId() {
        return customerId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
