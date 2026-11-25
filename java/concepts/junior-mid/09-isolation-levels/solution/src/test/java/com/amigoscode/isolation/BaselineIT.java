package com.amigoscode.isolation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amigoscode.isolation.statement.Statement;
import com.amigoscode.isolation.support.IntegrationTest;
import com.amigoscode.isolation.withdrawal.InsufficientFundsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The bank's rules with one customer at a time. These pass from the start and must stay green. */
@Tag("baseline")
class BaselineIT extends IntegrationTest {

    @Test
    @DisplayName("Withdrawal: 100 from the current account leaves 400, and the new balance is returned")
    void withdrawalLowersTheBalance() {
        long newBalance = withdrawalService.withdraw(CURRENT, 100);

        assertThat(newBalance).as("returned balance").isEqualTo(400);
        assertThat(balance(CURRENT)).as("current account").isEqualTo(400);
        assertThat(balance(SAVINGS)).as("savings account").isEqualTo(500);
    }

    @Test
    @DisplayName("Withdrawal: one account may go below zero while both accounts together stay at zero or above")
    void oneAccountMayGoNegative() {
        withdrawalService.withdraw(CURRENT, 800);

        assertThat(balance(CURRENT)).as("current account").isEqualTo(-300);
        assertThat(combinedBalance()).as("both accounts together").isEqualTo(200);
    }

    @Test
    @DisplayName("Withdrawal: refused when both accounts together cannot cover it, and nothing changes")
    void refusedWhenTheAccountsTogetherCannotCoverIt() {
        assertThatThrownBy(() -> withdrawalService.withdraw(CURRENT, 1001))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(balance(CURRENT)).as("current account").isEqualTo(500);
        assertThat(combinedBalance()).as("both accounts together").isEqualTo(1000);
    }

    @Test
    @DisplayName("Statement: with nobody else writing, it lists both accounts and they add up to the total")
    void statementAddsUpWhenNobodyElseIsWriting() {
        Statement statement = statementService.statement("alice");

        assertThat(statement.lines()).as("lines").hasSize(2);
        assertThat(statement.accountCount()).as("account count").isEqualTo(2);
        assertThat(statement.total()).as("total").isEqualTo(1000);
        assertThat(statement.addsUp()).as("lines add up to the total").isTrue();
    }
}
