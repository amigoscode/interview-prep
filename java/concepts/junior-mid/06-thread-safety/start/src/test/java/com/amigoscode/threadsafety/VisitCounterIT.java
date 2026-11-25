package com.amigoscode.threadsafety;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.threadsafety.support.IntegrationTest;
import com.amigoscode.threadsafety.support.Race;
import com.amigoscode.threadsafety.visits.VisitCounter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Task 2, against the bean Spring built: the one object every request thread shares. */
@Tag("task2")
class VisitCounterIT extends IntegrationTest {

    private static final int THREADS = 8;
    private static final int VISITS_EACH = 100_000;
    private static final int ROUNDS = 5;

    @Test
    @DisplayName("Counter: the VisitCounter bean, shared by 8 threads, counts all 800,000 visits and GET /api/visits reports them")
    void theSharedBeanCountsEveryVisit() throws Exception {
        VisitCounter counter = context.getBean(VisitCounter.class);
        for (int round = 1; round <= ROUNDS; round++) {
            counter.reset();

            assertThat(Race.describe(Race.run(THREADS, VISITS_EACH, (thread, i) -> counter.hit()))).isEmpty();

            assertThat(counter.count())
                    .withFailMessage("%,d visits were recorded, but the counter says %,d. %,d visits were lost.",
                            THREADS * VISITS_EACH, counter.count(), THREADS * VISITS_EACH - counter.count())
                    .isEqualTo(THREADS * VISITS_EACH);
        }
        assertThat(get("/api/visits").body()).contains("\"visits\":" + THREADS * VISITS_EACH);
    }
}
