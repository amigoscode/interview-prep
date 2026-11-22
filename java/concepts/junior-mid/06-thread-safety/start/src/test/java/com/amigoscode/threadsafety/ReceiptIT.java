package com.amigoscode.threadsafety;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.threadsafety.receipt.Receipt;
import com.amigoscode.threadsafety.receipt.ReceiptService;
import com.amigoscode.threadsafety.support.IntegrationTest;
import com.amigoscode.threadsafety.support.Meeting;
import com.amigoscode.threadsafety.support.Race;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Task 6. The loyalty lookup happens in the middle of ReceiptService.receiptFor. The stub holds
 * every customer there until all of them have arrived (see Meeting), so every request is inside
 * the method at the same moment. Not left to chance: with request data in fields, somebody gets
 * the wrong receipt. Every time.
 */
@Tag("task6")
class ReceiptIT extends IntegrationTest {

    @Autowired
    private ReceiptService receipts;

    @Test
    @DisplayName("Receipt: Alice and Bob pay at the same moment. Each gets a receipt with their own name and their own amount")
    void aliceAndBobGetTheirOwnReceipt() {
        Meeting meeting = new Meeting(2);
        loyaltyClient.everyoneIsLookedUpAtTheSameMoment(meeting);
        Receipt[] got = new Receipt[2];

        List<Throwable> thrown = Race.run(meeting, 2, 1, (thread, i) -> got[thread] = thread == 0
                ? receipts.receiptFor("alice", 2_500)
                : receipts.receiptFor("bob", 900));

        assertThat(Race.describe(thrown)).isEmpty();
        assertThat(got[0]).as("the receipt Alice was shown").isEqualTo(new Receipt("alice", 2_500, 25));
        assertThat(got[1]).as("the receipt Bob was shown").isEqualTo(new Receipt("bob", 900, 9));
    }

    @Test
    @DisplayName("Receipt: 20 customers pay at the same moment. Nobody is shown another customer's receipt")
    void nobodyGetsAnotherCustomersReceipt() {
        int customers = 20;
        Meeting meeting = new Meeting(customers);
        loyaltyClient.everyoneIsLookedUpAtTheSameMoment(meeting);
        Receipt[] got = new Receipt[customers];

        List<Throwable> thrown = Race.run(meeting, customers, 1,
                (thread, i) -> got[thread] = receipts.receiptFor("customer-" + thread, 1_000 * (thread + 1)));

        assertThat(Race.describe(thrown)).isEmpty();
        List<String> wrong = new ArrayList<>();
        for (int c = 0; c < customers; c++) {
            Receipt expected = new Receipt("customer-" + c, 1_000 * (c + 1), 10 * (c + 1));
            if (!expected.equals(got[c])) {
                wrong.add("customer-" + c + " was shown " + got[c]);
            }
        }
        assertThat(wrong)
                .withFailMessage("%d of %d customers were shown the wrong receipt. For example: %s",
                        wrong.size(), customers, wrong.isEmpty() ? "" : wrong.get(0))
                .isEmpty();
    }
}
