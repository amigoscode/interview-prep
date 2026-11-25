package com.amigoscode.threadsafety;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.threadsafety.pages.PageViewStats;
import com.amigoscode.threadsafety.support.Race;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Task 4, steps 1 and 2. Unit tests: plain Java, no Spring. Like count++, a map operation has no
 * place where a test could pause a thread, so these tests use numbers: threads that start
 * together, many calls each, several times over.
 *
 * The first test is kept small on purpose. A HashMap that is hit harder than this can end up with
 * a loop inside it, and then a thread goes round in circles for ever. If that happens here, the
 * test fails after 20 seconds and tells you where the thread is stuck.
 */
@Tag("task4")
class PageViewStatsTest {

    private static final int ROUNDS = 5;

    @Test
    @DisplayName("Pages: 4 threads view 1,000 different pages each. All 4,000 pages are in the map, with 1 view each")
    void noPageIsLost() {
        int threads = 4;
        int pagesEach = 1_000;
        for (int round = 1; round <= ROUNDS; round++) {
            PageViewStats stats = new PageViewStats();

            List<Throwable> thrown = Race.run(threads, pagesEach,
                    (thread, i) -> stats.record("page-" + thread + "-" + i));

            assertThat(Race.describe(thrown)).as("thrown by record()").isEmpty();
            int missing = 0;
            for (int thread = 0; thread < threads; thread++) {
                for (int i = 0; i < pagesEach; i++) {
                    if (stats.views("page-" + thread + "-" + i) != 1) {
                        missing++;
                    }
                }
            }
            assertThat(missing)
                    .withFailMessage("%,d of %,d pages were viewed once and are missing from the map.",
                            missing, threads * pagesEach)
                    .isZero();
            assertThat(stats.pages())
                    .withFailMessage("%,d different pages were viewed, but the map says it holds %,d.",
                            threads * pagesEach, stats.pages())
                    .isEqualTo(threads * pagesEach);
        }
    }

    @Test
    @DisplayName("Pages: 8 threads view the same page 50,000 times each. The page has exactly 400,000 views")
    void noViewOfAHotPageIsLost() {
        int threads = 8;
        int viewsEach = 50_000;
        for (int round = 1; round <= ROUNDS; round++) {
            PageViewStats stats = new PageViewStats();

            List<Throwable> thrown = Race.run(threads, viewsEach, (thread, i) -> stats.record("home"));

            assertThat(Race.describe(thrown)).as("thrown by record()").isEmpty();
            assertThat(stats.views("home"))
                    .withFailMessage("'home' was viewed %,d times, but the map says %,d. %,d views were lost.",
                            threads * viewsEach, stats.views("home"), threads * viewsEach - stats.views("home"))
                    .isEqualTo(threads * viewsEach);
        }
    }
}
