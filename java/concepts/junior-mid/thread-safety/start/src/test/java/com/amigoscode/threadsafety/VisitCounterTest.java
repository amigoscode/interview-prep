package com.amigoscode.threadsafety;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.threadsafety.support.Race;
import com.amigoscode.threadsafety.visits.VisitCounter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Task 2. Unit test: plain Java, no Spring. There is no place to pause a thread in the middle of
 * count++, so this test cannot force the bad interleaving. It uses numbers instead: 8 threads that
 * start together and make 100,000 calls each, several times over.
 */
@Tag("task2")
class VisitCounterTest {

    private static final int THREADS = 8;
    private static final int VISITS_EACH = 100_000;
    private static final int ROUNDS = 5;

    @Test
    @DisplayName("Counter: 8 threads x 100,000 visits = 800,000. Every visit is counted")
    void everyVisitIsCounted() {
        for (int round = 1; round <= ROUNDS; round++) {
            VisitCounter counter = new VisitCounter();

            assertThat(Race.describe(Race.run(THREADS, VISITS_EACH, (thread, i) -> counter.hit()))).isEmpty();

            int lost = THREADS * VISITS_EACH - counter.count();
            assertThat(counter.count())
                    .withFailMessage("%,d visits were recorded, but the counter says %,d. %,d visits were lost.",
                            THREADS * VISITS_EACH, counter.count(), lost)
                    .isEqualTo(THREADS * VISITS_EACH);
        }
    }
}
