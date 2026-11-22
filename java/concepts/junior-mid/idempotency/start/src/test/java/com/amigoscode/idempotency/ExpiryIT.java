package com.amigoscode.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.idempotency.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Task 8. The cleanup ships with the project, so these tests pass from the start. They pin down
 * what expiry means, including the part that is a trade-off and not a bug.
 */
@Tag("task8")
class ExpiryIT extends IntegrationTest {

    @Test
    @DisplayName("Expiry: the cleanup deletes the keys whose expires_at is in the past and keeps the others")
    void cleanupDeletesOnlyExpiredKeys() {
        jdbc.update("insert into idempotency_key (customer_id, idempotency_key, expires_at) values (?, 'old', now() - interval '1 minute')", MARIA);
        jdbc.update("insert into idempotency_key (customer_id, idempotency_key, expires_at) values (?, 'older', now() - interval '3 days')", MARIA);
        jdbc.update("insert into idempotency_key (customer_id, idempotency_key) values (?, 'fresh')", MARIA);

        int deleted = cleanup.deleteExpired();

        assertThat(deleted).as("rows deleted").isEqualTo(2);
        assertThat(jdbc.queryForList("select idempotency_key from idempotency_key", String.class))
                .as("keys left").containsExactly("fresh");
    }

    @Test
    @DisplayName("Expiry: once a key has expired and been deleted, a retry with it is a new payment and charges again (by design)")
    void afterExpiryTheSameKeyIsANewPayment() {
        String key = newKey();
        mariaPays(2_500, key);

        jdbc.update("update idempotency_key set expires_at = now() - interval '1 second'");
        cleanup.deleteExpired();
        mariaPays(2_500, key);

        assertThat(paymentRows()).as("payment rows").isEqualTo(2);
        assertThat(balance(MARIA)).as("balance after two charges of 25.00").isEqualTo(5_000);
    }
}
