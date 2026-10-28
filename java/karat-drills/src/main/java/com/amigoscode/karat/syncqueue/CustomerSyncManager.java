package com.amigoscode.karat.syncqueue;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * We are building Customer Sync Queue, a back-end system that sends customer changes to an
 * external CRM. A customer has an ID, a name, an email, a phone, a company and the last values
 * confirmed synced to the CRM. A queued update is one pending change to one field of a customer;
 * it is PENDING, PROCESSING, SUCCEEDED, FAILED or CANCELLED, and a FAILED update says whether the
 * failure was RETRYABLE or NON_RETRYABLE. Only PENDING updates and retryable FAILED updates are
 * eligible for synchronization.
 *
 * <p>Part 1: {@code CustomerSyncQueueTest} is not passing because of a bug in this class. Read
 * the code, run the test, fix the bug.
 *
 * <p>Part 2: support agents need to queue a customer field change, or cancel one that is no
 * longer needed, so the queue stays accurate before changes reach the CRM. Implement
 * {@link #queueUpdate} and {@link #cancelPendingUpdates}.
 */
public class CustomerSyncManager {

    public static final Set<String> SUPPORTED_FIELDS = Set.of("name", "email", "phone", "company");

    public final Map<String, Customer> customers = new HashMap<>();
    public final Map<Integer, QueuedUpdate> updates = new HashMap<>();
    public int nextUpdateId = 1;

    public void addCustomer(Customer customer) {
        customers.put(customer.customerId, customer);
    }

    /**
     * The number of updates that are eligible for synchronization: the PENDING ones plus the
     * FAILED ones whose failure was RETRYABLE.
     */
    public int countPendingUpdates() {
        int count = 0;
        for (QueuedUpdate update : updates.values()) {
            if (isEligibleForSync(update)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Part 2.1: queues a new update for one field of a customer. A new PENDING update is added
     * only when all of the following hold:
     * <ol>
     *   <li>the customer exists,</li>
     *   <li>the field is one of the supported fields: name, email, phone, company
     *       ({@link #SUPPORTED_FIELDS}), and</li>
     *   <li>the new value differs from the customer's current value of that field.</li>
     * </ol>
     * The new update receives the next available update ID (and the counter moves on), has status
     * PENDING, zero attempts and no error type, and is stored in {@link #updates}. Returns the
     * created update, or null when any validation fails: the request is then ignored.
     */
    public QueuedUpdate queueUpdate(String customerId, String fieldName, String newValue, int createdAt) {
        Customer customer = customers.get(customerId);
        if (customer == null || !SUPPORTED_FIELDS.contains(fieldName)) {
            return null;
        }
        if (Objects.equals(currentValue(customer, fieldName), newValue)) {
            return null;
        }
        QueuedUpdate update = new QueuedUpdate(
                nextUpdateId++, customerId, fieldName, newValue, createdAt, "PENDING", 0, null);
        updates.put(update.updateId, update);
        return update;
    }

    /**
     * Part 2.2: cancels all PENDING updates for the given customer and field by changing their
     * status to CANCELLED. Updates with any other status are left untouched. Returns the number of
     * updates cancelled, or 0 if the customer does not exist or no matching updates are found.
     */
    public int cancelPendingUpdates(String customerId, String fieldName) {
        if (!customers.containsKey(customerId)) {
            return 0;
        }
        int cancelled = 0;
        for (QueuedUpdate update : updates.values()) {
            if (update.customerId.equals(customerId)
                    && update.fieldName.equals(fieldName)
                    && "PENDING".equals(update.status)) {
                update.status = "CANCELLED";
                cancelled++;
            }
        }
        return cancelled;
    }

    private static boolean isEligibleForSync(QueuedUpdate update) {
        // Bug was that every FAILED update counted, including NON_RETRYABLE ones that will never
        // sync. Only a RETRYABLE failure goes back into the queue.
        return "PENDING".equals(update.status)
                || ("FAILED".equals(update.status) && "RETRYABLE".equals(update.errorType));
    }

    /** The customer's current value of a supported field, as held on our side. */
    private static String currentValue(Customer customer, String fieldName) {
        return switch (fieldName) {
            case "name" -> customer.name;
            case "email" -> customer.email;
            case "phone" -> customer.phone;
            case "company" -> customer.company;
            default -> throw new IllegalArgumentException("unsupported field: " + fieldName);
        };
    }
}
