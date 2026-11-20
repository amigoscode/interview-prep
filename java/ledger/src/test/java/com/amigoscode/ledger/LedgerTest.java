package com.amigoscode.ledger;

import com.amigoscode.ledger.model.Account;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerTest {

    @Test
    void projectIsWiredUp() {
        assertThat(new Ledger(new ArrayList<>())).isNotNull();
    }

    // Remove @Disabled to start story 1. Red first, then make it green.
    @Test
    void openedAccountHasOpeningBalance() {
        Ledger ledger = new Ledger(new ArrayList<>());
        ledger.open("A", new BigDecimal("100.00"));
        assertThat(ledger.balance("A")).isEqualByComparingTo("100.00");
    }

    @Test
    void openWhenAccountIdAlreadyExists() {
        List<Account> accountList = new ArrayList<>();
        accountList.add(new Account("A",BigDecimal.valueOf(100.00)));
        Ledger ledger = new Ledger(accountList);

        ledger.open("A", new BigDecimal("100.00"));
        assertThatThrownBy(() -> ledger.open("A", new BigDecimal("100.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A");
    }

}
