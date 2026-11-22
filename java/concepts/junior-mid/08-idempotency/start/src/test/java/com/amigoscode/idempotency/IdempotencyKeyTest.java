package com.amigoscode.idempotency;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amigoscode.idempotency.key.IdempotencyKey;
import com.amigoscode.idempotency.key.InvalidIdempotencyKeyException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit tests: no Spring, no database. */
@Tag("baseline")
class IdempotencyKeyTest {

    @Test
    @DisplayName("Key rules: a UUID is a valid key, and so is no key at all")
    void acceptsAUuidAndNoKey() {
        assertThatCode(() -> IdempotencyKey.validate(UUID.randomUUID().toString())).doesNotThrowAnyException();
        assertThatCode(() -> IdempotencyKey.validate("order-1042_attempt")).doesNotThrowAnyException();
        assertThatCode(() -> IdempotencyKey.validate(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Key rules: a blank key, a key over 64 characters or a key with spaces is rejected")
    void rejectsBlankLongAndOddKeys() {
        assertThatThrownBy(() -> IdempotencyKey.validate("")).isInstanceOf(InvalidIdempotencyKeyException.class);
        assertThatThrownBy(() -> IdempotencyKey.validate("   ")).isInstanceOf(InvalidIdempotencyKeyException.class);
        assertThatThrownBy(() -> IdempotencyKey.validate("k".repeat(65))).isInstanceOf(InvalidIdempotencyKeyException.class);
        assertThatThrownBy(() -> IdempotencyKey.validate("two words")).isInstanceOf(InvalidIdempotencyKeyException.class);
        assertThatThrownBy(() -> IdempotencyKey.validate("drop;table")).isInstanceOf(InvalidIdempotencyKeyException.class);
    }
}
