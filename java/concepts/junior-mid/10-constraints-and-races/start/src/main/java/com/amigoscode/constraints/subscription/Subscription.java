package com.amigoscode.constraints.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private long userId;

    @Enumerated(EnumType.STRING)
    private Plan plan;

    private int seats;

    /** Monthly price of one seat after the coupon, in cents. */
    @Column(name = "price_cents")
    private int priceCents;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "started_at")
    private Instant startedAt;

    protected Subscription() {
    }

    public Subscription(long userId, Plan plan, int seats, int priceCents) {
        this.userId = userId;
        this.plan = plan;
        this.seats = seats;
        this.priceCents = priceCents;
        this.status = Status.ACTIVE;
        this.startedAt = Instant.now();
    }

    public void cancel() {
        this.status = Status.CANCELLED;
    }

    public Long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public Plan getPlan() {
        return plan;
    }

    public int getSeats() {
        return seats;
    }

    public int getPriceCents() {
        return priceCents;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    @Override
    public String toString() {
        return "Subscription #" + id + " of user " + userId + " " + status;
    }
}
