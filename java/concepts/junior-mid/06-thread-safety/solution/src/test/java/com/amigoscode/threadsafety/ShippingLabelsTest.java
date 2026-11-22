package com.amigoscode.threadsafety;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.threadsafety.shipping.ShippingLabels;
import com.amigoscode.threadsafety.support.Race;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Task 6. Unit test: plain Java, no Spring. The formatter is a JDK class, so the test cannot pause
 * a thread inside it. It uses numbers instead: 8 threads, each with its own date, 20,000 labels each.
 */
@Tag("task6")
class ShippingLabelsTest {

    private static final int THREADS = 8;
    private static final int LABELS_EACH = 20_000;
    private static final int ROUNDS = 5;
    private static final DateTimeFormatter EXPECTED = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    @Test
    @DisplayName("Labels: 8 threads print labels for 8 different dates, 20,000 each. Every label shows its own date")
    void everyLabelShowsItsOwnDate() {
        for (int round = 1; round <= ROUNDS; round++) {
            ShippingLabels labels = new ShippingLabels();
            AtomicInteger wrong = new AtomicInteger();
            AtomicInteger threw = new AtomicInteger();
            AtomicReference<String> example = new AtomicReference<>();

            List<Throwable> thrown = Race.run(THREADS, LABELS_EACH, (thread, i) -> {
                // Thread 0 always asks for 05 Jan 2026, thread 1 for 17 Feb 2027, and so on.
                LocalDate shipDate = LocalDate.of(2026 + thread, 1 + thread, 5 + 12 * (thread % 2));
                try {
                    String label = labels.labelFor(shipDate);
                    if (!label.equals("Ships on " + EXPECTED.format(shipDate))) {
                        wrong.incrementAndGet();
                        example.compareAndSet(null, "asked for " + shipDate + ", got \"" + label + "\"");
                    }
                } catch (RuntimeException e) {
                    threw.incrementAndGet();
                }
            });

            assertThat(Race.describe(thrown)).isEmpty();
            assertThat(wrong.get() + threw.get())
                    .withFailMessage("Of %,d labels, %,d show a date that was not asked for and %,d threw an exception. "
                            + "For example: %s", THREADS * LABELS_EACH, wrong.get(), threw.get(), example.get())
                    .isZero();
        }
    }
}
