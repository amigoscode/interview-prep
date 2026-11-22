package com.amigoscode.transactional;

import static com.amigoscode.transactional.demo.Bank.ALICE;
import static com.amigoscode.transactional.demo.Bank.BOB;
import static com.amigoscode.transactional.demo.Bank.CAROL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amigoscode.transactional.account.AccountFrozenException;
import com.amigoscode.transactional.support.IntegrationTest;
import com.amigoscode.transactional.trace.TransactionTrace.Kind;
import com.amigoscode.transactional.transfer.TransferService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("task2")
class SelfInvocationIT extends IntegrationTest {

    @Autowired
    TransferService transferService;

    @Test
    @DisplayName("Transfer: when the receiver's account is frozen, the sender keeps the money and nothing is missing")
    void failedTransferChangesNoBalance() {
        assertThatThrownBy(() -> transferService.transfer(ALICE, CAROL, 50))
                .isInstanceOf(AccountFrozenException.class);

        assertThat(balance(ALICE)).as("Alice's balance after the failed transfer").isEqualTo(100);
        assertThat(balance(CAROL)).as("Carol's balance after the failed transfer").isEqualTo(0);
        assertThat(totalMoney()).as("all the money in the bank").isEqualTo(1100);
    }

    @Test
    @DisplayName("Transfer: the whole transfer runs in ONE database transaction, and the failure rolls it back")
    void failedTransferIsOneTransactionThatRollsBack() {
        assertThatThrownBy(() -> transferService.transfer(ALICE, CAROL, 50))
                .isInstanceOf(AccountFrozenException.class);

        assertThat(transactions(Kind.BEGIN)).as("database transactions opened by one transfer").isEqualTo(1);
        assertThat(transactions(Kind.ROLLBACK)).as("transactions rolled back").isEqualTo(1);
        assertThat(transactions(Kind.COMMIT)).as("transactions committed").isEqualTo(0);
    }

    @Test
    @DisplayName("Transfer: a transfer that succeeds moves the money")
    void successfulTransferMovesTheMoney() {
        transferService.transfer(ALICE, BOB, 30);

        assertThat(balance(ALICE)).as("Alice's balance").isEqualTo(70);
        assertThat(balance(BOB)).as("Bob's balance").isEqualTo(30);
        assertThat(totalMoney()).as("all the money in the bank").isEqualTo(1100);
    }
}
