package com.amigoscode.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amigoscode.idempotency.key.IdempotencyKeyReusedException;
import com.amigoscode.idempotency.key.RequestHash;
import com.amigoscode.idempotency.payment.PaymentRequest;
import com.amigoscode.idempotency.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("task7")
class DifferentBodyIT extends IntegrationTest {

    @Test
    @DisplayName("Mismatch: the same key with a different amount is rejected with IdempotencyKeyReusedException (a 422)")
    void sameKeyDifferentAmountIsRejected() {
        String key = newKey();
        mariaPays(2_500, key);

        assertThatThrownBy(() -> mariaPays(4_000, key)).isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    @DisplayName("Mismatch: the rejected request charges nothing, and the first payment is untouched")
    void rejectedRequestChargesNothing() {
        String key = newKey();
        mariaPays(2_500, key);

        try {
            mariaPays(4_000, key);
        } catch (IdempotencyKeyReusedException expected) {
            // this is the answer we want
        }

        assertThat(paymentRows()).as("payment rows").isEqualTo(1);
        assertThat(cardProcessor.calls()).as("times the card was charged").isEqualTo(1);
        assertThat(balance(MARIA)).as("balance after paying 25.00 once, starting from 100.00").isEqualTo(7_500);
    }

    @Test
    @DisplayName("Mismatch: the hash of the request is stored with the key")
    void requestHashIsStoredWithTheKey() {
        String key = newKey();
        mariaPays(2_500, key);

        String stored = jdbc.queryForObject(
                "select request_hash from idempotency_key where customer_id = ? and idempotency_key = ?",
                String.class, MARIA, key);

        assertThat(stored).as("request_hash column").isEqualTo(RequestHash.of(new PaymentRequest(MARIA, 2_500)));
    }
}
