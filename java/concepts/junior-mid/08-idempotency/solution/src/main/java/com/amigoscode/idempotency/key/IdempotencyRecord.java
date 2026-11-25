package com.amigoscode.idempotency.key;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/** One row of idempotency_key: a key a customer has used, and the answer that request got. */
@Entity
@Table(name = "idempotency_key")
@DynamicUpdate // an UPDATE only lists the columns that changed, which keeps ./demo --sql readable
public class IdempotencyRecord {

    /** How long a key is remembered. After that the cleanup deletes the row. */
    public static final Duration TIME_TO_LIVE = Duration.ofHours(24);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id")
    private long customerId;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "request_hash")
    private String requestHash;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(long customerId, String idempotencyKey, Long paymentId) {
        this.customerId = customerId;
        this.idempotencyKey = idempotencyKey;
        this.paymentId = paymentId;
        this.createdAt = Instant.now();
        this.expiresAt = this.createdAt.plus(TIME_TO_LIVE);
    }

    public Long getId() {
        return id;
    }

    public long getCustomerId() {
        return customerId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(Long paymentId) {
        this.paymentId = paymentId;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(Integer responseStatus) {
        this.responseStatus = responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
