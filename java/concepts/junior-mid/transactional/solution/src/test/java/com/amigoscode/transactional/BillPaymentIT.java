package com.amigoscode.transactional;

import static com.amigoscode.transactional.demo.Bank.ALICE;
import static com.amigoscode.transactional.demo.Bank.BOB;
import static com.amigoscode.transactional.demo.Bank.CAROL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amigoscode.transactional.bill.BillRejectedException;
import com.amigoscode.transactional.bill.BillService;
import com.amigoscode.transactional.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("task4")
class BillPaymentIT extends IntegrationTest {

    @Autowired
    BillService billService;

    @Test
    @DisplayName("Bill: when the bill is rejected with a checked exception, the payer keeps the money")
    void rejectedBillChangesNoBalance() {
        assertThatThrownBy(() -> billService.payBill(ALICE, CAROL, 50))
                .isInstanceOf(BillRejectedException.class);

        assertThat(balance(ALICE)).as("Alice's balance after the rejected bill").isEqualTo(100);
        assertThat(totalMoney()).as("all the money in the bank").isEqualTo(1100);
    }

    @Test
    @DisplayName("Bill: a bill that is accepted moves the money")
    void acceptedBillMovesTheMoney() throws BillRejectedException {
        billService.payBill(ALICE, BOB, 40);

        assertThat(balance(ALICE)).as("Alice's balance").isEqualTo(60);
        assertThat(balance(BOB)).as("Bob's balance").isEqualTo(40);
    }
}
