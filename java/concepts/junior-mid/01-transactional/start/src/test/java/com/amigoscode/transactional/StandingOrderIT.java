package com.amigoscode.transactional;

import static com.amigoscode.transactional.demo.Bank.ALICE;
import static com.amigoscode.transactional.demo.Bank.BOB;
import static com.amigoscode.transactional.demo.Bank.CAROL;
import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.transactional.standingorder.StandingOrderJob;
import com.amigoscode.transactional.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("task4")
class StandingOrderIT extends IntegrationTest {

    @Autowired
    StandingOrderJob job;

    @Test
    @DisplayName("Standing order: when it fails, the sender keeps the money and nothing is missing")
    void failedStandingOrderChangesNoBalance() {
        job.run(ALICE, CAROL, 50);

        assertThat(balance(ALICE)).as("Alice's balance after the failed standing order").isEqualTo(100);
        assertThat(totalMoney()).as("all the money in the bank").isEqualTo(1100);
    }

    @Test
    @DisplayName("Standing order: the job reports a failure as false and never throws")
    void theJobReportsFailureWithoutThrowing() {
        boolean paid = job.run(ALICE, CAROL, 50);

        assertThat(paid).as("job.run(...) for a payment that fails").isFalse();
    }

    @Test
    @DisplayName("Standing order: a run that succeeds returns true and moves the money")
    void successfulStandingOrderMovesTheMoney() {
        boolean paid = job.run(ALICE, BOB, 25);

        assertThat(paid).as("job.run(...) for a payment that works").isTrue();
        assertThat(balance(ALICE)).as("Alice's balance").isEqualTo(75);
        assertThat(balance(BOB)).as("Bob's balance").isEqualTo(25);
    }
}
