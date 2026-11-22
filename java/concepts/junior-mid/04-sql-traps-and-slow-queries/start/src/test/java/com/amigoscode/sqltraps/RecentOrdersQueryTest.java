package com.amigoscode.sqltraps;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.sqltraps.shop.Query;
import com.amigoscode.sqltraps.shop.ShopQueries;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit tests: no Spring, no database. They look at the SQL text and the values bound to it. */
@Tag("task8")
class RecentOrdersQueryTest {

    @Test
    @DisplayName("Recent orders: the SQL names its columns (no SELECT *) and ends with LIMIT ?, bound to the page size")
    void namesItsColumnsAndHasALimit() {
        Query query = ShopQueries.recentOrders(20);
        String sql = query.sql().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");

        assertThat(sql).as("SELECT * fetches every column, also the ones nobody shows").doesNotContain("*");
        assertThat(sql).as("the page size is a parameter, not a number glued into the SQL").endsWith("limit ?");
        assertThat(query.args()).as("value bound to LIMIT ?").containsExactly(20);
    }

    @Test
    @DisplayName("Recent orders: the page size is capped: a client asking for 1,000,000 rows gets 100, and 0 becomes 1")
    void thePageSizeIsCapped() {
        assertThat(ShopQueries.recentOrders(1_000_000).args()).as("LIMIT for size=1000000").containsExactly(ShopQueries.MAX_PAGE_SIZE);
        assertThat(ShopQueries.recentOrders(100).args()).as("LIMIT for size=100").containsExactly(100);
        assertThat(ShopQueries.recentOrders(0).args()).as("LIMIT for size=0").containsExactly(1);
    }
}
