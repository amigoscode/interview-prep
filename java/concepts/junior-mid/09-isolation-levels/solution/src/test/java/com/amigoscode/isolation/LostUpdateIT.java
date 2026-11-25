package com.amigoscode.isolation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.amigoscode.isolation.checkpoint.Checkpoint;
import com.amigoscode.isolation.demo.Anomaly;
import com.amigoscode.isolation.demo.Level;
import com.amigoscode.isolation.demo.Outcome;
import com.amigoscode.isolation.demo.Outcome.Verdict;
import com.amigoscode.isolation.support.IntegrationTest;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;

@Tag("task4")
class LostUpdateIT extends IntegrationTest {

    @Test
    @DisplayName("READ COMMITTED: lost update. Two withdrawals of 100 are paid out and the balance only goes from 500 to 400")
    void readCommittedLosesAnUpdate() {
        Outcome outcome = lab().run(Anomaly.LOST_UPDATE, Level.READ_COMMITTED);

        assertThat(outcome.seen()).as("the balance each session read").containsExactly("500", "500");
        assertThat(outcome.finalValue()).as("balance after two withdrawals of 100").isEqualTo(400);
        assertThat(outcome.verdict()).isEqualTo(Verdict.HAPPENED);
    }

    @Test
    @DisplayName("REPEATABLE READ: the second writer is cancelled with SQLSTATE 40001, could not serialize access due to concurrent update")
    void repeatableReadCancelsTheSecondWriter() {
        Outcome outcome = lab().run(Anomaly.LOST_UPDATE, Level.REPEATABLE_READ);

        assertThat(outcome.verdict()).isEqualTo(Verdict.SERIALIZATION_FAILURE);
        assertThat(outcome.sqlState()).as("SQLSTATE").isEqualTo("40001");
        assertThat(outcome.error()).isEqualTo("could not serialize access due to concurrent update");
        assertThat(outcome.finalValue()).as("balance after the one withdrawal that committed").isEqualTo(400);
    }

    @Test
    @DisplayName("Withdrawer: one attempt that loses the race fails with Spring's CannotAcquireLockException, caused by SQLSTATE 40001")
    void theLosingAttemptFailsWithCannotAcquireLockException() {
        // In the middle of our attempt, another customer session withdraws 100 from the same account and commits.
        checkpoint.onFirst(Checkpoint.FUNDS_CHECKED, () -> inAnotherThread(() -> withdrawer.withdraw(CURRENT, 100)));

        Throwable thrown = catchThrowable(() -> withdrawer.withdraw(CURRENT, 100));

        assertThat(thrown).as("what one losing attempt throws").isInstanceOf(CannotAcquireLockException.class);
        assertThat(thrown).rootCause().isInstanceOf(SQLException.class);
        assertThat(((SQLException) rootCause(thrown)).getSQLState()).as("SQLSTATE").isEqualTo("40001");
        assertThat(balance(CURRENT)).as("balance: only the other withdrawal committed").isEqualTo(400);
    }

    @Test
    @DisplayName("Withdrawal: two withdrawals of 100 from the same account both go through, one on its second attempt, and the balance is 300")
    void bothWithdrawalsGoThroughAndNothingIsLost() {
        AtomicReference<Throwable> other = new AtomicReference<>();
        checkpoint.onFirst(Checkpoint.FUNDS_CHECKED,
                () -> other.set(inAnotherThread(() -> withdrawalService.withdraw(CURRENT, 100))));

        long newBalance = withdrawalService.withdraw(CURRENT, 100);

        assertThat(other.get()).as("what the other withdrawal threw").isNull();
        assertThat(balance(CURRENT)).as("balance after two withdrawals of 100").isEqualTo(300);
        assertThat(newBalance).as("balance returned to the customer who retried").isEqualTo(300);
        assertThat(checkpoint.arrivals(Checkpoint.FUNDS_CHECKED))
                .as("attempts: ours, the other customer's, and our second one").isEqualTo(3);
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root;
    }
}
