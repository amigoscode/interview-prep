package com.amigoscode.pagination.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private Long id;

    @Column(name = "customer_id")
    private long customerId;

    private String status;

    @Column(name = "total_cents")
    private int totalCents;

    @Column(name = "created_at")
    private Instant createdAt;

    protected Order() {
    }

    public Order(long id, long customerId, String status, int totalCents, Instant createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.status = status;
        this.totalCents = totalCents;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public long getCustomerId() {
        return customerId;
    }

    public String getStatus() {
        return status;
    }

    public int getTotalCents() {
        return totalCents;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
