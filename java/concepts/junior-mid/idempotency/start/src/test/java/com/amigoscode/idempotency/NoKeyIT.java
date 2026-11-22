package com.amigoscode.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.idempotency.payment.PaymentResult;
import com.amigoscode.idempotency.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Task 1. This test passes on purpose: it pins down the bug so you can see exactly what goes wrong. */
@Tag("task1")
class NoKeyIT extends IntegrationTest {

    @Test
    @DisplayName("No key: the same payment sent twice charges the customer twice (this documents the bug)")
    void samePaymentSentTwiceWithoutAKeyChargesTwice() {
        PaymentResult first = mariaPays(2_500, null);
        PaymentResult retry = mariaPays(2_500, null);

        assertThat(paymentRows()).as("payment rows for one payment sent twice").isEqualTo(2);
        assertThat(cardProcessor.calls()).as("times the card was charged").isEqualTo(2);
        assertThat(balance(MARIA)).as("balance after paying 25.00 once, starting from 100.00").isEqualTo(5_000);
        assertThat(retry.body().paymentId()).as("the retry is a different payment").isNotEqualTo(first.body().paymentId());
    }
}
