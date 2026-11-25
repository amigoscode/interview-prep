package com.amigoscode.sqltraps;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.sqltraps.db.Plan;
import com.amigoscode.sqltraps.shop.Query;
import com.amigoscode.sqltraps.shop.ShopQueries;
import com.amigoscode.sqltraps.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Task 8: the first page of the admin screen shows 20 orders, so the query fetches 20 orders. */
@Tag("task8")
class RecentOrdersIT extends IntegrationTest {

    private static final Query PAGE = ShopQueries.recentOrders(20);

    @BeforeEach
    void data() {
        bigShop();
    }

    @Test
    @DisplayName("Recent orders: the query returns 20 rows, the newest first (83321, 165642, 247963, ...)")
    void returnsOnePageNewestFirst() {
        // Only the ids are fetched here: in start/ the query returns the whole table.
        List<Long> ids = jdbc.queryForList("select id from (" + PAGE.sql() + ") q", Long.class, PAGE.args().toArray());
        List<Long> newest = jdbc.queryForList("select id from orders order by created_at desc limit 20", Long.class);

        assertThat(ids).as("rows returned for a page of 20").hasSize(20);
        assertThat(ids).as("order ids, newest first").startsWith(83_321L, 165_642L, 247_963L).containsExactlyElementsOf(newest);
    }

    @Test
    @DisplayName("Recent orders: Postgres stops after one page: a Limit step, and 20 rows read from the table")
    void postgresStopsAfterOnePage() {
        Plan plan = plan(PAGE);

        assertThat(plan.has("Limit")).as("plan has a Limit step: %s", plan).isTrue();
        assertThat(plan.rowsRead()).as("rows read from orders to show 20").isEqualTo(20);
    }

    @Test
    @DisplayName("Recent orders: only the 5 columns the page shows are fetched, not the address and the notes")
    void fetchesOnlyTheColumnsThePageShows() {
        List<String> columns = jdbc.query("select * from (" + PAGE.sql() + ") q limit 0", result -> {
            List<String> names = new ArrayList<>();
            for (int c = 1; c <= result.getMetaData().getColumnCount(); c++) {
                names.add(result.getMetaData().getColumnLabel(c));
            }
            return names;
        }, PAGE.args().toArray());

        assertThat(columns).as("columns of the result").containsExactly("id", "customer_id", "status", "total_cents", "created_at");
    }
}
