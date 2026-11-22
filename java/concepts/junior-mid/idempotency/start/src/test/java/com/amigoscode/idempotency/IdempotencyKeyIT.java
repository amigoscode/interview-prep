package com.amigoscode.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.idempotency.payment.PaymentRequest;
import com.amigoscode.idempotency.payment.PaymentResult;
import com.amigoscode.idempotency.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("task2")
class IdempotencyKeyIT extends IntegrationTest {

    @Test
    @DisplayName("Key: a retry with the same key charges the customer once")
    void retryWithTheSameKeyChargesOnce() {
        String key = newKey();

        mariaPays(2_500, key);
        mariaPays(2_500, key);
        mariaPays(2_500, key);

        assertThat(paymentRows()).as("payment rows for one payment sent 3 times").isEqualTo(1);
        assertThat(cardProcessor.calls()).as("times the card was charged").isEqualTo(1);
        assertThat(balance(MARIA)).as("balance after paying 25.00 once, starting from 100.00").isEqualTo(7_500);
    }

    @Test
    @DisplayName("Key: the retry is answered with the payment that already exists, not a new one")
    void retryIsAnsweredWithTheSamePayment() {
        String key = newKey();

        PaymentResult first = mariaPays(2_500, key);
        PaymentResult retry = mariaPays(2_500, key);

        assertThat(retry.body().paymentId()).as("payment id in the retry's answer").isEqualTo(first.body().paymentId());
        assertThat(retry.body().amountCents()).as("amount in the retry's answer").isEqualTo(2_500);
    }

    @Test
    @DisplayName("Key: the server remembers the key in the idempotency_key table, with the payment it belongs to")
    void keyIsStoredWithItsPayment() {
        String key = newKey();

        PaymentResult first = mariaPays(2_500, key);

        assertThat(keyRows()).as("rows in idempotency_key").isEqualTo(1);
        Long paymentId = jdbc.queryForObject(
                "select payment_id from idempotency_key where customer_id = ? and idempotency_key = ?",
                Long.class, MARIA, key);
        assertThat(paymentId).as("payment_id stored with the key").isEqualTo(first.body().paymentId());
    }

    @Test
    @DisplayName("Key: two different keys are two payments")
    void differentKeysAreDifferentPayments() {
        mariaPays(2_500, newKey());
        mariaPays(2_500, newKey());

        assertThat(paymentRows()).as("payment rows").isEqualTo(2);
        assertThat(balance(MARIA)).as("balance after two payments of 25.00").isEqualTo(5_000);
    }

    @Test
    @DisplayName("Key: the same key from two different customers is two payments (a key is scoped to its customer)")
    void keysAreScopedPerCustomer() {
        String key = newKey();

        mariaPays(2_500, key);
        paymentService.pay(key, new PaymentRequest(JAMAL, 2_500));

        assertThat(paymentRows()).as("payment rows").isEqualTo(2);
        assertThat(balance(MARIA)).as("Maria's balance").isEqualTo(7_500);
        assertThat(balance(JAMAL)).as("Jamal's balance").isEqualTo(7_500);
    }
}
