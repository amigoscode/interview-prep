package com.amigoscode.isolation;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.isolation.demo.Anomaly;
import com.amigoscode.isolation.demo.Level;
import com.amigoscode.isolation.demo.Outcome;
import com.amigoscode.isolation.demo.Outcome.Verdict;
import com.amigoscode.isolation.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Task 1. These tests pass on purpose: they pin down what Postgres does at its default level, and
 * at the level below it. Each one drives two sessions with plain SQL, one statement at a time.
 */
@Tag("task1")
class ReadCommittedIT extends IntegrationTest {

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("Default: Postgres runs at READ COMMITTED, and so does a Spring transaction that sets no isolation level")
    void theDefaultIsReadCommitted() {
        String postgresDefault = jdbc.queryForObject("show default_transaction_isolation", String.class);
        String springDefault = new TransactionTemplate(transactionManager)
                .execute(status -> isolationLevelOfThisTransaction());

        assertThat(postgresDefault).as("default_transaction_isolation").isEqualTo("read committed");
        assertThat(springDefault).as("level of a default Spring transaction").isEqualTo("read committed");
    }

    @Test
    @DisplayName("READ COMMITTED: non-repeatable read. One transaction reads a balance twice and gets 500, then 600")
    void nonRepeatableRead() {
        Outcome outcome = lab().run(Anomaly.NON_REPEATABLE_READ, Level.READ_COMMITTED);

        assertThat(outcome.seen()).as("what session A read, in one transaction").containsExactly("500", "600");
        assertThat(outcome.verdict()).isEqualTo(Verdict.HAPPENED);
    }

    @Test
    @DisplayName("READ COMMITTED: phantom read. The same count returns 2 rows, then 3")
    void phantomRead() {
        Outcome outcome = lab().run(Anomaly.PHANTOM, Level.READ_COMMITTED);

        assertThat(outcome.seen()).as("what session A counted, in one transaction").containsExactly("2", "3");
        assertThat(outcome.verdict()).isEqualTo(Verdict.HAPPENED);
    }

    @Test
    @DisplayName("READ UNCOMMITTED: no dirty read in Postgres. Another session's uncommitted change stays invisible")
    void noDirtyReadEvenAtReadUncommitted() {
        Outcome outcome = lab().run(Anomaly.DIRTY_READ, Level.READ_UNCOMMITTED);

        assertThat(outcome.seen()).as("what session A read while B's uncommitted 0 was in the table")
                .containsExactly("500");
        assertThat(outcome.verdict()).isEqualTo(Verdict.PREVENTED);
    }

    @Test
    @DisplayName("READ UNCOMMITTED: for every anomaly it behaves exactly like READ COMMITTED")
    void readUncommittedBehavesAsReadCommitted() {
        for (Anomaly anomaly : Anomaly.values()) {
            Outcome readUncommitted = lab().run(anomaly, Level.READ_UNCOMMITTED);
            Outcome readCommitted = lab().run(anomaly, Level.READ_COMMITTED);

            assertThat(readUncommitted.verdict()).as(anomaly.flag()).isEqualTo(readCommitted.verdict());
            assertThat(readUncommitted.seen()).as(anomaly.flag()).isEqualTo(readCommitted.seen());
        }
    }
}
