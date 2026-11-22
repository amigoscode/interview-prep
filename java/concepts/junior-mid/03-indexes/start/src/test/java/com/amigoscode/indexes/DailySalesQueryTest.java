package com.amigoscode.indexes;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.indexes.shop.Query;
import com.amigoscode.indexes.shop.ShopQueries;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit tests: no Spring, no database. They look at the SQL text and the values bound to it. */
@Tag("task6")
class DailySalesQueryTest {

    private static final LocalDate DAY = LocalDate.of(2025, 6, 15);

    @Test
    @DisplayName("Rewrite: the daily sales SQL compares created_at directly, with no function or cast around the column")
    void theColumnIsNotWrappedInAFunction() {
        String sql = ShopQueries.dailySales(DAY).sql().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");

        assertThat(sql).as("a function around the column hides it from the index").doesNotContainPattern("\\w+\\s*\\(\\s*created_at");
        assertThat(sql).as("a cast on the column hides it from the index").doesNotContainPattern("created_at\\s*::");
        assertThat(sql).as("the lower bound is included").contains("created_at >= ?");
        assertThat(sql).as("the upper bound is excluded").contains("created_at < ?");
    }

    @Test
    @DisplayName("Rewrite: the range is half-open: from midnight of the day (included) to midnight of the next day (excluded)")
    void theRangeIsHalfOpen() {
        Query query = ShopQueries.dailySales(DAY);

        assertThat(query.args())
                .as("values bound to the two ? placeholders")
                .containsExactly(LocalDateTime.of(2025, 6, 15, 0, 0), LocalDateTime.of(2025, 6, 16, 0, 0));
    }
}
