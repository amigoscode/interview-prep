package com.amigoscode.isolation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amigoscode.isolation.withdrawal.InsufficientFundsException;
import com.amigoscode.isolation.withdrawal.WithdrawalConflictException;
import com.amigoscode.isolation.withdrawal.WithdrawalService;
import com.amigoscode.isolation.withdrawal.Withdrawer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unit tests: no Spring, no database. Withdrawer is replaced by a scripted stub, so only the retry
 * loop in WithdrawalService is under test and the whole class runs in milliseconds.
 */
@Tag("task4")
class WithdrawalServiceRetryTest {

    @Test
    @DisplayName("Retry: when Postgres cancels the transaction (40001), the withdrawal is tried again and succeeds")
    void retriesAfterASerializationFailure() {
        ScriptedWithdrawer withdrawer = new ScriptedWithdrawer(serializationFailure(), 300L);

        long newBalance = new WithdrawalService(withdrawer).withdraw(1, 100);

        assertThat(newBalance).as("returned balance").isEqualTo(300);
        assertThat(withdrawer.attempts).as("attempts").isEqualTo(2);
    }

    @Test
    @DisplayName("Retry: after 3 cancelled attempts it stops and throws WithdrawalConflictException")
    void givesUpAfterThreeAttempts() {
        ScriptedWithdrawer withdrawer = new ScriptedWithdrawer(serializationFailure());

        assertThatThrownBy(() -> new WithdrawalService(withdrawer).withdraw(1, 100))
                .isInstanceOf(WithdrawalConflictException.class);

        assertThat(withdrawer.attempts).as("attempts").isEqualTo(3);
    }

    @Test
    @DisplayName("Retry: insufficient funds is not retried, because trying again cannot change the answer")
    void doesNotRetryInsufficientFunds() {
        ScriptedWithdrawer withdrawer = new ScriptedWithdrawer(new InsufficientFundsException(1, 100, 0));

        assertThatThrownBy(() -> new WithdrawalService(withdrawer).withdraw(1, 100))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(withdrawer.attempts).as("attempts").isEqualTo(1);
    }

    @Test
    @DisplayName("Retry: WithdrawalService is not @Transactional, so every attempt gets a new transaction")
    void theRetryLoopIsOutsideTheTransaction() throws NoSuchMethodException {
        assertThat(AnnotatedElementUtils.hasAnnotation(WithdrawalService.class, Transactional.class))
                .as("@Transactional on the WithdrawalService class").isFalse();
        assertThat(AnnotatedElementUtils.hasAnnotation(
                WithdrawalService.class.getMethod("withdraw", long.class, long.class), Transactional.class))
                .as("@Transactional on WithdrawalService.withdraw").isFalse();
    }

    /** What Spring throws for SQLSTATE 40001. LostUpdateIT proves that against a real database. */
    private static CannotAcquireLockException serializationFailure() {
        return new CannotAcquireLockException("could not serialize access due to concurrent update");
    }

    /**
     * Plays back a script: each call to withdraw returns the next balance or throws the next
     * exception. The last entry repeats for ever.
     */
    private static final class ScriptedWithdrawer extends Withdrawer {

        private final Deque<Object> script;
        private int attempts;

        ScriptedWithdrawer(Object... outcomes) {
            super(null, null);
            this.script = new ArrayDeque<>(List.of(outcomes));
        }

        @Override
        public long withdraw(long accountId, long amount) {
            attempts++;
            Object outcome = script.size() > 1 ? script.poll() : script.peek();
            if (outcome instanceof RuntimeException exception) {
                throw exception;
            }
            return (Long) outcome;
        }
    }
}
