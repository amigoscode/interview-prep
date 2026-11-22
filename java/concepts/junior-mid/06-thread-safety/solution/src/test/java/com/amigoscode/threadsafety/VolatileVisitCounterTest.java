package com.amigoscode.threadsafety;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.threadsafety.support.Race;
import com.amigoscode.threadsafety.visits.VolatileVisitCounter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Task 3. This test passes on purpose: it pins down that volatile does not fix count++. */
@Tag("task3")
class VolatileVisitCounterTest {

    private static final int THREADS = 8;
    private static final int VISITS_EACH = 100_000;
    private static final int MAX_ROUNDS = 20;

    @Test
    @DisplayName("Volatile: 8 threads x 100,000 visits on a volatile int, and visits are still lost (this documents the bug)")
    void volatileStillLosesVisits() {
        int lost = 0;
        for (int round = 1; round <= MAX_ROUNDS && lost == 0; round++) {
            VolatileVisitCounter counter = new VolatileVisitCounter();
            Race.run(THREADS, VISITS_EACH, (thread, i) -> counter.hit());
            lost = THREADS * VISITS_EACH - counter.count();
        }

        assertThat(lost).as("visits lost by the volatile counter").isGreaterThan(0);
    }
}
