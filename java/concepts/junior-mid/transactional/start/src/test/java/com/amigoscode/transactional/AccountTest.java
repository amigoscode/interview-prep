package com.amigoscode.transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amigoscode.transactional.account.Account;
import com.amigoscode.transactional.account.AccountFrozenException;
import com.amigoscode.transactional.account.InsufficientFundsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit tests: no Spring, no database. The rules of one account, which every scenario relies on. */
@Tag("baseline")
class AccountTest {

    @Test
    @DisplayName("Account: a withdrawal lowers the balance and a deposit raises it")
    void withdrawAndDepositChangeTheBalance() {
        Account account = new Account(1, "Alice", 100, false);

        account.withdraw(30);
        account.deposit(5);

        assertThat(account.getBalance()).isEqualTo(75);
    }

    @Test
    @DisplayName("Account: taking out more than the balance is refused with InsufficientFundsException")
    void cannotWithdrawMoreThanTheBalance() {
        Account account = new Account(1, "Alice", 100, false);

        assertThatThrownBy(() -> account.withdraw(500)).isInstanceOf(InsufficientFundsException.class);

        assertThat(account.getBalance()).as("balance after the refused withdrawal").isEqualTo(100);
    }

    @Test
    @DisplayName("Account: a frozen account refuses deposits and withdrawals with AccountFrozenException")
    void frozenAccountRefusesEverything() {
        Account account = new Account(3, "Carol", 20, true);

        assertThatThrownBy(() -> account.deposit(50)).isInstanceOf(AccountFrozenException.class);
        assertThatThrownBy(() -> account.withdraw(10)).isInstanceOf(AccountFrozenException.class);

        assertThat(account.getBalance()).as("balance of the frozen account").isEqualTo(20);
    }

    @Test
    @DisplayName("Account: an amount of zero or less is refused")
    void amountMustBePositive() {
        Account account = new Account(1, "Alice", 100, false);

        assertThatThrownBy(() -> account.withdraw(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.deposit(-5)).isInstanceOf(IllegalArgumentException.class);
    }
}
