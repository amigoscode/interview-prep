package com.amigoscode.transactional;

import static com.amigoscode.transactional.demo.Bank.ALICE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amigoscode.transactional.account.InsufficientFundsException;
import com.amigoscode.transactional.atm.AtmService;
import com.amigoscode.transactional.support.IntegrationTest;
import com.amigoscode.transactional.trace.TransactionTrace.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("task6")
class AtmIT extends IntegrationTest {

    @Autowired
    AtmService atmService;

    @Test
    @DisplayName("ATM: a refused withdrawal leaves the balance alone, and its audit row survives the rollback")
    void auditRowSurvivesARefusedWithdrawal() {
        assertThatThrownBy(() -> atmService.withdraw(ALICE, 500))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(balance(ALICE)).as("Alice's balance after the refused withdrawal").isEqualTo(100);
        assertThat(auditRows()).as("audit rows after the refused withdrawal").isEqualTo(1);
    }

    @Test
    @DisplayName("ATM: the audit row is written in a transaction of its own, which commits while the withdrawal's rolls back")
    void auditRowIsWrittenInItsOwnTransaction() {
        assertThatThrownBy(() -> atmService.withdraw(ALICE, 500))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(transactions(Kind.BEGIN)).as("database transactions opened: the withdrawal's and the audit's").isEqualTo(2);
        assertThat(transactions(Kind.COMMIT)).as("transactions committed: the audit's").isEqualTo(1);
        assertThat(transactions(Kind.ROLLBACK)).as("transactions rolled back: the withdrawal's").isEqualTo(1);
    }

    @Test
    @DisplayName("ATM: a withdrawal that succeeds takes the money and writes one audit row")
    void successfulWithdrawalTakesTheMoneyAndIsAudited() {
        atmService.withdraw(ALICE, 40);

        assertThat(balance(ALICE)).as("Alice's balance").isEqualTo(60);
        assertThat(auditRows()).as("audit rows").isEqualTo(1);
    }
}
