package com.amigoscode.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.idempotency.payment.PaymentResponse;
import com.amigoscode.idempotency.payment.PaymentResult;
import com.amigoscode.idempotency.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("task6")
class ReplayIT extends IntegrationTest {

    @Test
    @DisplayName("Replay: the retry gets the same status code and the same body as the original, even after the balance has changed")
    void retryGetsTheOriginalAnswer() {
        String key = newKey();

        PaymentResult original = mariaPays(2_500, key);   // balance after: 75.00
        mariaPays(1_000, newKey());                       // another payment: the balance is now 65.00
        PaymentResult retry = mariaPays(2_500, key);

        assertThat(original.status()).as("status of the original").isEqualTo(201);
        assertThat(retry.status()).as("status of the retry").isEqualTo(original.status());
        assertThat(retry.body()).as("body of the retry").isEqualTo(original.body());
        assertThat(retry.body().balanceAfterCents()).as("balance after, as the original reported it").isEqualTo(7_500);
    }

    @Test
    @DisplayName("Replay: the original is not marked as a replay, the retry is")
    void onlyTheRetryIsMarkedAsAReplay() {
        String key = newKey();

        PaymentResult original = mariaPays(2_500, key);
        PaymentResult retry = mariaPays(2_500, key);

        assertThat(original.replayed()).as("original marked as a replay").isFalse();
        assertThat(retry.replayed()).as("retry marked as a replay").isTrue();
    }

    @Test
    @DisplayName("Replay: the status code and the body are stored with the key, in the same transaction as the payment")
    void answerIsStoredWithTheKey() {
        String key = newKey();

        PaymentResult original = mariaPays(2_500, key);

        Map<String, Object> row = jdbc.queryForMap(
                "select response_status, response_body from idempotency_key where customer_id = ? and idempotency_key = ?",
                MARIA, key);
        assertThat(row.get("response_status")).as("response_status column").isEqualTo(201);
        assertThat(row.get("response_body")).as("response_body column").isNotNull();
        assertThat(PaymentResponse.fromJson((String) row.get("response_body")))
                .as("response_body column, read back").isEqualTo(original.body());
    }
}
