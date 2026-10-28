package com.amigoscode.karat.syncqueue;

/**
 * A single pending change to one field of a customer, waiting to be pushed to the external CRM.
 *
 * <p>{@code status} is one of PENDING, PROCESSING, SUCCEEDED, FAILED and CANCELLED. A FAILED
 * update also carries an {@code errorType} of RETRYABLE (a transient failure worth trying again)
 * or NON_RETRYABLE (a permanent rejection that will not succeed on retry); for every other status
 * {@code errorType} is null. Statuses and error types are plain strings on purpose, that is how
 * the original code models them.
 */
public class QueuedUpdate {
    public final int updateId;
    public final String customerId;
    public final String fieldName;
    public final String newValue;
    public final int createdAt;
    public String status;
    public int attempts;
    public String errorType;

    public QueuedUpdate(
            int updateId,
            String customerId,
            String fieldName,
            String newValue,
            int createdAt,
            String status,
            int attempts,
            String errorType) {
        this.updateId = updateId;
        this.customerId = customerId;
        this.fieldName = fieldName;
        this.newValue = newValue;
        this.createdAt = createdAt;
        this.status = status;
        this.attempts = attempts;
        this.errorType = errorType;
    }
}
