package com.amigoscode.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.idempotency.key.RequestHash;
import com.amigoscode.idempotency.payment.PaymentRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit tests: no Spring, no database. */
@Tag("task7")
class RequestHashTest {

    @Test
    @DisplayName("Hash: the same request always gives the same hash")
    void sameRequestSameHash() {
        assertThat(RequestHash.of(new PaymentRequest(1, 2_500)))
                .isEqualTo(RequestHash.of(new PaymentRequest(1, 2_500)));
    }

    @Test
    @DisplayName("Hash: a different amount or a different customer gives a different hash")
    void differentRequestDifferentHash() {
        String hash = RequestHash.of(new PaymentRequest(1, 2_500));

        assertThat(RequestHash.of(new PaymentRequest(1, 4_000))).as("hash for another amount").isNotEqualTo(hash);
        assertThat(RequestHash.of(new PaymentRequest(2, 2_500))).as("hash for another customer").isNotEqualTo(hash);
        assertThat(RequestHash.of(new PaymentRequest(12, 500))).as("hash when the digits run together: 12 + 500 vs 1 + 2500")
                .isNotEqualTo(hash);
    }

    @Test
    @DisplayName("Hash: it is a SHA-256 in hex (64 characters), not a copy of the request")
    void hashIsSha256Hex() {
        assertThat(RequestHash.of(new PaymentRequest(1, 2_500))).matches("[0-9a-f]{64}");
    }
}
