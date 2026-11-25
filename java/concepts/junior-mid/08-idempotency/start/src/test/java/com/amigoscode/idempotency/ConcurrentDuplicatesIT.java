package com.amigoscode.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amigoscode.idempotency.payment.InsufficientFundsException;
import com.amigoscode.idempotency.payment.PaymentRequest;
import com.amigoscode.idempotency.support.Duplicates;
import com.amigoscode.idempotency.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

@Tag("task4")
class ConcurrentDuplicatesIT extends IntegrationTest {

    private static final PaymentRequest MARIA_PAYS_25 = new PaymentRequest(MARIA, 2_500);

    @Test
    @DisplayName("Race: two requests with the same key at the same moment charge the customer once, and the card once")
    void doubleClickChargesOnce() {
        cardProcessor.everyoneChecksTheKeyBeforeAnyoneCharges(2);

        Duplicates.send(paymentService, newKey(), MARIA_PAYS_25, 2);

        assertThat(paymentRows()).as("payment rows for one payment sent twice").isEqualTo(1);
        assertThat(cardProcessor.calls()).as("times the card was charged").isEqualTo(1);
        assertThat(balance(MARIA)).as("balance after paying 25.00 once, starting from 100.00").isEqualTo(7_500);
    }

    @Test
    @DisplayName("Race: the request that loses is answered with the winner's payment, not with an error")
    void theLoserIsAnsweredWithTheWinnersPayment() {
        cardProcessor.everyoneChecksTheKeyBeforeAnyoneCharges(2);

        Duplicates.Result result = Duplicates.send(paymentService, newKey(), MARIA_PAYS_25, 2);

        assertThat(result.failures()).as("requests that failed (each would be an error response)").isEmpty();
        assertThat(result.answers()).as("requests that got an answer").hasSize(2);
        assertThat(result.paymentIds()).as("distinct payment ids in the two answers").hasSize(1);
    }

    @Test
    @DisplayName("Race: 8 requests with the same key at the same moment: one payment row, one charge, 8 answers")
    void manyDuplicatesChargeOnce() {
        cardProcessor.everyoneChecksTheKeyBeforeAnyoneCharges(8);

        Duplicates.Result result = Duplicates.send(paymentService, newKey(), MARIA_PAYS_25, 8);

        assertThat(result.failures()).as("requests that failed (each would be an error response)").isEmpty();
        assertThat(paymentRows()).as("payment rows for one payment sent 8 times").isEqualTo(1);
        assertThat(cardProcessor.calls()).as("times the card was charged").isEqualTo(1);
        assertThat(keyRows()).as("rows in idempotency_key").isEqualTo(1);
        assertThat(result.paymentIds()).as("distinct payment ids in the 8 answers").hasSize(1);
    }

    @Test
    @DisplayName("Race: the database itself refuses a second row for the same customer and key (UNIQUE constraint)")
    void databaseRefusesASecondRowForTheSameKey() {
        String insert = "insert into idempotency_key (customer_id, idempotency_key) values (?, ?)";
        jdbc.update(insert, MARIA, "same-key");

        assertThatThrownBy(() -> jdbc.update(insert, MARIA, "same-key"))
                .as("a second row for customer 1 and 'same-key'")
                .isInstanceOf(DuplicateKeyException.class);
        jdbc.update(insert, JAMAL, "same-key"); // another customer may use the same key
    }

    @Test
    @DisplayName("Race: a payment that fails leaves no key behind, so the same key can be tried again later")
    void failedPaymentLeavesNoKeyBehind() {
        String key = newKey();
        PaymentRequest tooMuch = new PaymentRequest(MARIA, 50_000);

        assertThatThrownBy(() -> paymentService.pay(key, tooMuch)).isInstanceOf(InsufficientFundsException.class);

        assertThat(keyRows()).as("rows in idempotency_key after a failed payment").isEqualTo(0);
        assertThat(paymentRows()).as("payment rows after a failed payment").isEqualTo(0);

        jdbc.update("update customer set balance_cents = 60000 where id = ?", MARIA); // Maria tops up
        paymentService.pay(key, tooMuch);

        assertThat(paymentRows()).as("payment rows after the same key is tried again").isEqualTo(1);
        assertThat(balance(MARIA)).as("balance after paying 500.00 out of 600.00").isEqualTo(10_000);
    }
}
