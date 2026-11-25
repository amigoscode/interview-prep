package com.amigoscode.isolation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.amigoscode.isolation.checkpoint.Checkpoint;
import com.amigoscode.isolation.demo.Anomaly;
import com.amigoscode.isolation.demo.Level;
import com.amigoscode.isolation.demo.Outcome;
import com.amigoscode.isolation.demo.Outcome.Verdict;
import com.amigoscode.isolation.support.IntegrationTest;
import com.amigoscode.isolation.withdrawal.InsufficientFundsException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("task6")
class WriteSkewIT extends IntegrationTest {

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("REPEATABLE READ: write skew. Both sessions see 1000, both withdraw 800, both commit, and the accounts hold -600")
    void repeatableReadAllowsWriteSkew() {
        Outcome outcome = lab().run(Anomaly.WRITE_SKEW, Level.REPEATABLE_READ);

        assertThat(outcome.seen()).as("the combined balance each session saw").containsExactly("1000", "1000");
        assertThat(outcome.failedSession()).as("session cancelled by the database").isNull();
        assertThat(outcome.finalValue()).as("both accounts together").isEqualTo(-600);
        assertThat(outcome.verdict()).isEqualTo(Verdict.HAPPENED);
    }

    @Test
    @DisplayName("SERIALIZABLE: the second session is cancelled at COMMIT with SQLSTATE 40001, and the accounts keep 200")
    void serializableCancelsOneOfTheTwo() {
        Outcome outcome = lab().run(Anomaly.WRITE_SKEW, Level.SERIALIZABLE);

        assertThat(outcome.verdict()).isEqualTo(Verdict.SERIALIZATION_FAILURE);
        assertThat(outcome.sqlState()).as("SQLSTATE").isEqualTo("40001");
        assertThat(outcome.error())
                .isEqualTo("could not serialize access due to read/write dependencies among transactions");
        assertThat(outcome.finalValue()).as("both accounts together").isEqualTo(200);
    }

    @Test
    @DisplayName("Spring: a transaction that Postgres cancels at COMMIT also arrives as CannotAcquireLockException")
    void aFailedCommitIsAlsoCannotAcquireLockException() {
        TransactionTemplate serializable = new TransactionTemplate(transactionManager);
        serializable.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
        String readBoth = "select sum(balance) from account where owner = 'alice'";

        // We read both accounts and write account 1. Before we commit, a second transaction reads
        // both accounts, writes account 2 and commits. Every statement of ours worked: only COMMIT fails.
        Throwable thrown = catchThrowable(() -> serializable.executeWithoutResult(ours -> {
            jdbc.queryForObject(readBoth, Long.class);
            jdbc.update("update account set balance = balance - 800 where id = 1");
            inAnotherThread(() -> serializable.executeWithoutResult(theirs -> {
                jdbc.queryForObject(readBoth, Long.class);
                jdbc.update("update account set balance = balance - 800 where id = 2");
            }));
        }));

        assertThat(thrown).as("what a failed COMMIT throws").isInstanceOf(CannotAcquireLockException.class);
        assertThat(thrown).hasMessageContaining("commit");
        assertThat(combinedBalance()).as("both accounts together").isEqualTo(200);
    }

    @Test
    @DisplayName("Spring: an isolation level asked for inside a transaction that is already open is silently ignored")
    void anInnerIsolationLevelIsIgnored() {
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        TransactionTemplate inner = new TransactionTemplate(transactionManager);
        inner.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);

        // The inner one joins the outer transaction (propagation REQUIRED, the default), which
        // started at the default level. A transaction has one level, fixed before its first query.
        String level = outer.execute(open -> inner.execute(joined -> isolationLevelOfThisTransaction()));

        assertThat(level).as("level inside the inner, 'serializable' transaction").isEqualTo("read committed");
    }

    @Test
    @DisplayName("Withdrawal: 800 from current and 800 from savings at the same time: one goes through, the other is refused, 200 is left")
    void onlyOneOfTheTwoWithdrawalsGoesThrough() {
        AtomicReference<Throwable> other = new AtomicReference<>();
        checkpoint.onFirst(Checkpoint.FUNDS_CHECKED,
                () -> other.set(inAnotherThread(() -> withdrawalService.withdraw(SAVINGS, 800))));

        Throwable ours = catchThrowable(() -> withdrawalService.withdraw(CURRENT, 800));

        assertThat(combinedBalance()).as("both accounts together (the rule: never below 0)").isEqualTo(200);
        assertThat(other.get()).as("what the withdrawal from savings threw").isNull();
        assertThat(ours).as("what the withdrawal from current threw, after its retry read the new balances")
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    @DisplayName("Withdrawal: every attempt runs in a SERIALIZABLE transaction")
    void everyAttemptRunsAtSerializable() {
        List<String> levels = Collections.synchronizedList(new ArrayList<>());
        checkpoint.onReached(Checkpoint.FUNDS_CHECKED, arrival -> levels.add(isolationLevelOfThisTransaction()));

        withdrawalService.withdraw(CURRENT, 100);

        assertThat(levels).as("isolation level in the middle of withdraw()").containsExactly("serializable");
    }
}
