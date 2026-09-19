package com.amigoscode.ledger;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerTest {

    @Test
    void projectIsWiredUp() {
        assertThat(new Ledger()).isNotNull();
    }

    // Remove @Disabled to start story 1. Red first, then make it green.
    @Test
    @Disabled("story 1: remove this line to begin")
    void openedAccountHasOpeningBalance() {
        Ledger ledger = new Ledger();
        ledger.open("A", new BigDecimal("100.00"));
        assertThat(ledger.balance("A")).isEqualByComparingTo("100.00");
    }
}
