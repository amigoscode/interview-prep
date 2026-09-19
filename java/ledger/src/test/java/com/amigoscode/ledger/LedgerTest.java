package com.amigoscode.ledger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Stories 1 and 2. Story 3 is in LedgerConcurrencyTest, story 4 in LedgerIdempotencyTest. */
class LedgerTest {

    Ledger ledger;

    @BeforeEach
    void setUp() {
        ledger = new Ledger();
        ledger.open("A", new BigDecimal("100.00"));
        ledger.open("B", new BigDecimal("50.00"));
    }

    @Nested
    class Story1Accounts {

        @Test
        void openedAccountHasOpeningBalance() {
            assertThat(ledger.balance("A")).isEqualByComparingTo("100.00");
        }

        @Test
        void openingBalanceMayBeZeroButNotNegative() {
            ledger.open("Z", BigDecimal.ZERO);
            assertThat(ledger.balance("Z")).isEqualByComparingTo("0");
            assertThatThrownBy(() -> ledger.open("N", new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void duplicateIdIsRejected() {
            assertThatThrownBy(() -> ledger.open("A", BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A");
        }

        @Test
        void unknownAccountIsRejectedEverywhere() {
            assertThatThrownBy(() -> ledger.balance("X")).isInstanceOf(AccountNotFoundException.class);
            assertThatThrownBy(() -> ledger.deposit("X", BigDecimal.ONE)).isInstanceOf(AccountNotFoundException.class);
            assertThatThrownBy(() -> ledger.withdraw("X", BigDecimal.ONE)).isInstanceOf(AccountNotFoundException.class);
        }

        @Test
        void depositAndWithdrawMoveTheBalance() {
            ledger.deposit("A", new BigDecimal("25.50"));
            ledger.withdraw("A", new BigDecimal("0.50"));
            assertThat(ledger.balance("A")).isEqualByComparingTo("125.00");
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-0.01", "-100"})
        void nonPositiveAmountsAreRejected(String amount) {
            assertThatThrownBy(() -> ledger.deposit("A", new BigDecimal(amount))).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ledger.withdraw("A", new BigDecimal(amount))).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void cannotWithdrawMoreThanTheBalance() {
            assertThatThrownBy(() -> ledger.withdraw("A", new BigDecimal("100.01")))
                .isInstanceOf(InsufficientFundsException.class);
            assertThat(ledger.balance("A")).isEqualByComparingTo("100.00");
        }

        @Test
        void canWithdrawExactlyTheBalance() {
            ledger.withdraw("A", new BigDecimal("100.00"));
            assertThat(ledger.balance("A")).isEqualByComparingTo("0");
        }
    }

    @Nested
    class Story2Transfer {

        @Test
        void transferMovesMoneyBetweenAccounts() {
            ledger.transfer("A", "B", new BigDecimal("30.00"));
            assertThat(ledger.balance("A")).isEqualByComparingTo("70.00");
            assertThat(ledger.balance("B")).isEqualByComparingTo("80.00");
        }

        @Test
        void insufficientFundsLeavesBothAccountsUntouched() {
            assertThatThrownBy(() -> ledger.transfer("A", "B", new BigDecimal("100.01")))
                .isInstanceOf(InsufficientFundsException.class);
            assertThat(ledger.balance("A")).isEqualByComparingTo("100.00");
            assertThat(ledger.balance("B")).isEqualByComparingTo("50.00");
        }

        @Test
        void transferToSameAccountIsRejected() {
            assertThatThrownBy(() -> ledger.transfer("A", "A", BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void unknownSourceOrDestinationIsRejected() {
            assertThatThrownBy(() -> ledger.transfer("X", "B", BigDecimal.ONE)).isInstanceOf(AccountNotFoundException.class);
            assertThatThrownBy(() -> ledger.transfer("A", "X", BigDecimal.ONE)).isInstanceOf(AccountNotFoundException.class);
            assertThat(ledger.balance("A")).isEqualByComparingTo("100.00");
        }

        @Test
        void nonPositiveAmountIsRejected() {
            assertThatThrownBy(() -> ledger.transfer("A", "B", BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ledger.transfer("A", "B", new BigDecimal("-5"))).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
