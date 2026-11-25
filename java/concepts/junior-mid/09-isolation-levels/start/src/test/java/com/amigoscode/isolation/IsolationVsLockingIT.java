package com.amigoscode.isolation;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.isolation.demo.Anomaly;
import com.amigoscode.isolation.demo.Lab;
import com.amigoscode.isolation.demo.Level;
import com.amigoscode.isolation.demo.Outcome;
import com.amigoscode.isolation.demo.Outcome.Verdict;
import com.amigoscode.isolation.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Task 8. These pass from the start: they pin down the difference between raising the isolation
 * level and taking a lock, on the same write-skew scenario.
 */
@Tag("task8")
class IsolationVsLockingIT extends IntegrationTest {

    @Test
    @DisplayName("A lock makes the other session WAIT: with SELECT ... FOR UPDATE at READ COMMITTED, B waits for A, then reads 200 and refuses")
    void forUpdateMakesTheSecondSessionWait() {
        Outcome outcome = lab().run(Anomaly.WRITE_SKEW, Level.READ_COMMITTED, new Lab.Options(true, 0));

        assertThat(outcome.waited()).as("session B had to wait for a lock").isTrue();
        assertThat(outcome.seen()).as("the combined balance each session saw").containsExactly("1000", "200");
        assertThat(outcome.failedSession()).as("session cancelled by the database").isNull();
        assertThat(outcome.finalValue()).as("both accounts together").isEqualTo(200);
        assertThat(outcome.verdict()).isEqualTo(Verdict.PREVENTED);
    }

    @Test
    @DisplayName("An isolation level makes nobody wait: at SERIALIZABLE both sessions read 1000 at once, and one is CANCELLED instead")
    void serializableCancelsInsteadOfWaiting() {
        Outcome outcome = lab().run(Anomaly.WRITE_SKEW, Level.SERIALIZABLE);

        assertThat(outcome.waited()).as("a session had to wait for a lock").isFalse();
        assertThat(outcome.seen()).as("the combined balance each session saw").containsExactly("1000", "1000");
        assertThat(outcome.failedSession()).as("session cancelled by the database").isEqualTo("B");
        assertThat(outcome.finalValue()).as("both accounts together").isEqualTo(200);
    }
}
