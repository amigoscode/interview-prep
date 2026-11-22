package com.amigoscode.transactional;

import static com.amigoscode.transactional.demo.Bank.ALICE;
import static com.amigoscode.transactional.demo.Bank.CAROL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amigoscode.transactional.account.AccountFrozenException;
import com.amigoscode.transactional.audit.AuditEntry;
import com.amigoscode.transactional.audit.AuditRepository;
import com.amigoscode.transactional.support.IntegrationTest;
import com.amigoscode.transactional.trace.TransactionTrace.Kind;
import com.amigoscode.transactional.trap.Ledger;
import com.amigoscode.transactional.trap.SplitTransferService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Task 7. These tests pass from the start: they pin down what REQUIRES_NEW does when one of the two transactions fails. */
@Tag("task7")
class RequiresNewTrapIT extends IntegrationTest {

    @Autowired
    SplitTransferService splitTransferService;

    @Autowired
    Ledger ledger;

    @Autowired
    AuditRepository auditLog;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("REQUIRES_NEW: the inner debit commits on its own, so when the outer transaction fails later, 50 is missing")
    void innerCommitSurvivesTheOuterRollback() {
        assertThatThrownBy(() -> splitTransferService.transfer(ALICE, CAROL, 50))
                .isInstanceOf(AccountFrozenException.class);

        assertThat(balance(ALICE)).as("Alice's balance: the debit was already committed").isEqualTo(50);
        assertThat(balance(CAROL)).as("Carol's balance").isEqualTo(0);
        assertThat(totalMoney()).as("all the money in the bank: 50 is gone").isEqualTo(1050);
        assertThat(transactions(Kind.BEGIN)).as("database transactions opened: outer, debit, credit").isEqualTo(3);
        assertThat(transactions(Kind.COMMIT)).as("transactions committed: the debit").isEqualTo(1);
        assertThat(transactions(Kind.ROLLBACK)).as("transactions rolled back: the credit and the outer one").isEqualTo(2);
    }

    @Test
    @DisplayName("REQUIRES_NEW: when the inner transaction fails and the outer one catches the exception, the outer one still commits")
    void outerCommitSurvivesTheInnerRollback() {
        TransactionTemplate outer = new TransactionTemplate(transactionManager);

        outer.executeWithoutResult(status -> {
            auditLog.save(new AuditEntry("OUTER", "written by the outer transaction"));
            try {
                ledger.credit(CAROL, 50);           // REQUIRES_NEW, fails: Carol is frozen
            } catch (AccountFrozenException e) {
                // caught: with REQUIRES_NEW the outer transaction was never touched
            }
        });

        assertThat(auditRows()).as("rows written by the outer transaction").isEqualTo(1);
        assertThat(transactions(Kind.COMMIT)).as("transactions committed: the outer one").isEqualTo(1);
        assertThat(transactions(Kind.ROLLBACK)).as("transactions rolled back: the inner one").isEqualTo(1);
    }
}
