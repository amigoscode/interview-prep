package com.amigoscode.transactional;

import static com.amigoscode.transactional.demo.Bank.ACME;
import static com.amigoscode.transactional.demo.Bank.ALICE;
import static com.amigoscode.transactional.demo.Bank.BOB;
import static com.amigoscode.transactional.demo.Bank.CAROL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amigoscode.transactional.payroll.PayrollService;
import com.amigoscode.transactional.support.IntegrationTest;
import com.amigoscode.transactional.trace.TransactionTrace.Kind;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.UnexpectedRollbackException;

/** Task 5. These tests pass from the start: they pin down the "rollback-only" surprise. */
@Tag("task5")
class PayrollIT extends IntegrationTest {

    @Autowired
    PayrollService payrollService;

    @Test
    @DisplayName("Rollback-only: the outer method catches the inner failure, and its commit still fails with UnexpectedRollbackException. Nobody is paid")
    void catchingTheInnerExceptionDoesNotSaveTheTransaction() {
        assertThatThrownBy(() -> payrollService.payEveryone(ACME, List.of(ALICE, BOB, CAROL), 100))
                .isInstanceOf(UnexpectedRollbackException.class)
                .hasMessageContaining("rollback-only");

        assertThat(balance(ALICE)).as("Alice's balance").isEqualTo(100);
        assertThat(balance(BOB)).as("Bob's balance").isEqualTo(0);
        assertThat(balance(ACME)).as("Acme's balance").isEqualTo(1000);
        assertThat(transactions(Kind.BEGIN)).as("database transactions opened: all three salaries joined one").isEqualTo(1);
    }

    @Test
    @DisplayName("Rollback-only: when every account can be paid, everyone is paid and the one transaction commits")
    void everyoneIsPaidWhenNothingFails() {
        int paid = payrollService.payEveryone(ACME, List.of(ALICE, BOB), 100);

        assertThat(paid).as("employees paid").isEqualTo(2);
        assertThat(balance(ALICE)).as("Alice's balance").isEqualTo(200);
        assertThat(balance(BOB)).as("Bob's balance").isEqualTo(100);
        assertThat(balance(ACME)).as("Acme's balance").isEqualTo(800);
        assertThat(transactions(Kind.COMMIT)).as("transactions committed").isEqualTo(1);
    }
}
