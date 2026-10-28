package com.amigoscode.karat.syncqueue;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This is not a complete test suite, but tests some basic functionality of the code and shows how
 * to use it. One test fails on purpose: that is part 1.
 */
@Disabled("syncqueue drill: remove this line to begin part 1")
class CustomerSyncQueueTest {

    private static Customer alice() {
        return new Customer("c1", "Alice Smith", "alice@example.com", "5551234567", "Acme",
                Map.of("name", "Alice Smith", "email", "alice@example.com"));
    }

    @Test
    void customer() {
        Customer customer = alice();

        assertThat(customer.customerId).isEqualTo("c1");
        assertThat(customer.name).isEqualTo("Alice Smith");
        assertThat(customer.email).isEqualTo("alice@example.com");
        assertThat(customer.phone).isEqualTo("5551234567");
        assertThat(customer.company).isEqualTo("Acme");
        assertThat(customer.lastSyncedValues).containsEntry("name", "Alice Smith");
    }

    @Test
    void countPendingUpdates() {
        CustomerSyncManager manager = new CustomerSyncManager();
        manager.addCustomer(alice());
        manager.updates.put(1, new QueuedUpdate(1, "c1", "name", "Alice Jones", 100, "PENDING", 0, null));
        manager.updates.put(2, new QueuedUpdate(2, "c1", "email", "aj@example.com", 101, "PENDING", 0, null));
        manager.updates.put(3, new QueuedUpdate(3, "c1", "phone", "5550000000", 102, "FAILED", 1, "RETRYABLE"));
        manager.updates.put(4, new QueuedUpdate(4, "c1", "company", "Globex", 103, "FAILED", 3, "NON_RETRYABLE"));
        manager.updates.put(5, new QueuedUpdate(5, "c1", "name", "A. Smith", 104, "SUCCEEDED", 1, null));
        manager.updates.put(6, new QueuedUpdate(6, "c1", "email", "old@example.com", 105, "CANCELLED", 0, null));
        manager.updates.put(7, new QueuedUpdate(7, "c1", "phone", "5551111111", 106, "PROCESSING", 1, null));

        // 2 PENDING + 1 retryable FAILED. The non-retryable failure will never sync.
        assertThat(manager.countPendingUpdates()).isEqualTo(3);
    }

    @Test
    @Disabled("part 2: remove this line when part 1 is green")
    void queueUpdate() {
        CustomerSyncManager manager = new CustomerSyncManager();
        manager.addCustomer(alice());

        assertThat(manager.queueUpdate("c99", "name", "Nobody", 100)).isNull(); // unknown customer
        assertThat(manager.queueUpdate("c1", "address", "1 Main St", 100)).isNull(); // unsupported field
        assertThat(manager.queueUpdate("c1", "name", "Alice Smith", 100)).isNull(); // value unchanged
        assertThat(manager.updates).isEmpty();

        QueuedUpdate first = manager.queueUpdate("c1", "name", "Alice Jones", 101);
        assertThat(first.updateId).isEqualTo(1);
        assertThat(first.customerId).isEqualTo("c1");
        assertThat(first.fieldName).isEqualTo("name");
        assertThat(first.newValue).isEqualTo("Alice Jones");
        assertThat(first.createdAt).isEqualTo(101);
        assertThat(first.status).isEqualTo("PENDING");
        assertThat(first.attempts).isEqualTo(0);
        assertThat(first.errorType).isNull();

        QueuedUpdate second = manager.queueUpdate("c1", "email", "aj@example.com", 102);
        assertThat(second.updateId).isEqualTo(2);
        assertThat(second.status).isEqualTo("PENDING");

        assertThat(manager.updates).containsOnlyKeys(1, 2);
        assertThat(manager.updates.get(1)).isSameAs(first);
        assertThat(manager.nextUpdateId).isEqualTo(3);
    }

    @Test
    @Disabled("part 2: remove this line when part 1 is green")
    void cancelPendingUpdates() {
        CustomerSyncManager manager = new CustomerSyncManager();
        manager.addCustomer(alice());
        manager.addCustomer(new Customer("c2", "Bob Jones", "bob@example.com", "5559876543", "Initech", Map.of()));
        manager.updates.put(1, new QueuedUpdate(1, "c1", "name", "Alice Jones", 100, "PENDING", 0, null));
        manager.updates.put(2, new QueuedUpdate(2, "c1", "name", "Alice J.", 101, "PENDING", 0, null));
        manager.updates.put(3, new QueuedUpdate(3, "c1", "name", "A. Smith", 102, "PROCESSING", 1, null));
        manager.updates.put(4, new QueuedUpdate(4, "c1", "name", "Alice S.", 103, "FAILED", 2, "RETRYABLE"));
        manager.updates.put(5, new QueuedUpdate(5, "c1", "email", "aj@example.com", 104, "PENDING", 0, null));
        manager.updates.put(6, new QueuedUpdate(6, "c2", "name", "Robert Jones", 105, "PENDING", 0, null));

        assertThat(manager.cancelPendingUpdates("c1", "name")).isEqualTo(2);

        assertThat(manager.updates.get(1).status).isEqualTo("CANCELLED");
        assertThat(manager.updates.get(2).status).isEqualTo("CANCELLED");
        assertThat(manager.updates.get(3).status).isEqualTo("PROCESSING"); // not pending: untouched
        assertThat(manager.updates.get(4).status).isEqualTo("FAILED"); // not pending: untouched
        assertThat(manager.updates.get(5).status).isEqualTo("PENDING"); // other field
        assertThat(manager.updates.get(6).status).isEqualTo("PENDING"); // other customer

        assertThat(manager.cancelPendingUpdates("c1", "name")).isEqualTo(0); // nothing left to cancel
        assertThat(manager.cancelPendingUpdates("c99", "name")).isEqualTo(0); // unknown customer
    }
}
