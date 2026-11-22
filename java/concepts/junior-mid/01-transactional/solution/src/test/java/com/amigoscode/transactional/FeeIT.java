package com.amigoscode.transactional;

import static com.amigoscode.transactional.demo.Bank.ALICE;
import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.transactional.fee.FeeService;
import com.amigoscode.transactional.support.IntegrationTest;
import com.amigoscode.transactional.trace.TransactionTrace.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("task8")
class FeeIT extends IntegrationTest {

    @Autowired
    FeeService feeService;

    @Test
    @DisplayName("Fee: charging the monthly fee takes 5 from the account")
    void monthlyFeeIsCharged() {
        feeService.chargeMonthlyFee(ALICE);

        assertThat(balance(ALICE)).as("Alice's balance after the fee").isEqualTo(95);
    }

    @Test
    @DisplayName("Fee: chargeMonthlyFee runs in one database transaction, which commits")
    void monthlyFeeRunsInOneTransaction() {
        feeService.chargeMonthlyFee(ALICE);

        assertThat(transactions(Kind.BEGIN)).as("database transactions opened").isEqualTo(1);
        assertThat(transactions(Kind.COMMIT)).as("transactions committed").isEqualTo(1);
    }
}
