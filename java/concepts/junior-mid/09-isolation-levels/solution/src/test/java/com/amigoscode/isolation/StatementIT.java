package com.amigoscode.isolation;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.isolation.checkpoint.Checkpoint;
import com.amigoscode.isolation.demo.Anomaly;
import com.amigoscode.isolation.demo.Level;
import com.amigoscode.isolation.demo.Outcome;
import com.amigoscode.isolation.demo.Outcome.Verdict;
import com.amigoscode.isolation.statement.Statement;
import com.amigoscode.isolation.support.IntegrationTest;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("task2")
class StatementIT extends IntegrationTest {

    @Test
    @DisplayName("REPEATABLE READ: one transaction reads a balance twice and gets 500 both times, although 600 is committed")
    void repeatableReadReadsTheSameValueTwice() {
        Outcome outcome = lab().run(Anomaly.NON_REPEATABLE_READ, Level.REPEATABLE_READ);

        assertThat(outcome.seen()).as("what session A read, in one transaction").containsExactly("500", "500");
        assertThat(outcome.finalValue()).as("committed balance").isEqualTo(600);
        assertThat(outcome.verdict()).isEqualTo(Verdict.PREVENTED);
    }

    @Test
    @DisplayName("REPEATABLE READ: no phantom in Postgres. The same count returns 2 rows both times, although 3 are committed")
    void repeatableReadHasNoPhantomsInPostgres() {
        Outcome outcome = lab().run(Anomaly.PHANTOM, Level.REPEATABLE_READ);

        assertThat(outcome.seen()).as("what session A counted, in one transaction").containsExactly("2", "2");
        assertThat(outcome.finalValue()).as("committed rows").isEqualTo(3);
        assertThat(outcome.verdict()).isEqualTo(Verdict.PREVENTED);
    }

    @Test
    @DisplayName("Statement: a deposit commits between its queries, and the lines still add up to the total")
    void aDepositInTheMiddleDoesNotBreakTheStatement() {
        checkpoint.onFirst(Checkpoint.LINES_READ,
                () -> inAnotherSession("update account set balance = balance + 100 where id = 1"));

        Statement statement = statementService.statement("alice");

        assertThat(balance(CURRENT)).as("the deposit is committed").isEqualTo(600);
        assertThat(statement.total()).as("total, when the lines add up to " + statement.sumOfLines())
                .isEqualTo(statement.sumOfLines());
    }

    @Test
    @DisplayName("Statement: an account is opened between its queries, and the count still matches the lines")
    void aNewAccountInTheMiddleDoesNotBreakTheStatement() {
        checkpoint.onFirst(Checkpoint.LINES_READ, () -> inAnotherSession(
                "insert into account (id, owner, name, balance) values (3, 'alice', 'holiday', 250)"));

        Statement statement = statementService.statement("alice");

        assertThat(statement.accountCount()).as("account count, when the statement lists "
                + statement.lines().size() + " lines").isEqualTo(statement.lines().size());
        assertThat(statement.total()).as("total, when the lines add up to " + statement.sumOfLines())
                .isEqualTo(statement.sumOfLines());
    }

    @Test
    @DisplayName("Statement: all of its queries run in one REPEATABLE READ transaction")
    void statementRunsAtRepeatableRead() {
        AtomicReference<String> level = new AtomicReference<>();
        checkpoint.onFirst(Checkpoint.LINES_READ, () -> level.set(isolationLevelOfThisTransaction()));

        statementService.statement("alice");

        assertThat(level.get()).as("isolation level in the middle of statement()").isEqualTo("repeatable read");
    }

    @Test
    @DisplayName("Statement: the snapshot ends with the transaction. The next statement shows the deposit")
    void theNextStatementSeesTheDeposit() {
        checkpoint.onFirst(Checkpoint.LINES_READ,
                () -> inAnotherSession("update account set balance = balance + 100 where id = 1"));
        statementService.statement("alice");

        Statement next = statementService.statement("alice");

        assertThat(next.total()).as("total of the next statement").isEqualTo(1100);
        assertThat(next.addsUp()).as("lines add up to the total").isTrue();
    }
}
