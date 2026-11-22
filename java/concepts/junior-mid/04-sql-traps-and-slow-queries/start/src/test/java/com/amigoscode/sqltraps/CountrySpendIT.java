package com.amigoscode.sqltraps;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.sqltraps.db.Plan;
import com.amigoscode.sqltraps.shop.ShopQueries;
import com.amigoscode.sqltraps.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Task 7: the country report must read the orders table once, not once per customer. */
@Tag("task7")
class CountrySpendIT extends IntegrationTest {

    private static final long CUSTOMERS = 20_008;
    private static final long ORDERS = 300_012;

    /** The plan of the report on the big shop. Taken once: in start/ the query needs a few seconds. */
    private static Plan plan;

    @BeforeEach
    void data() {
        bigShop();
        if (plan == null) {
            plan = plan(ShopQueries.countrySpend("IE"));
        }
    }

    @Test
    @DisplayName("Country report: the 50 Irish customers with the right numbers, also the 10 who never paid (already passing: keep it that way)")
    void returnsEveryCustomerOfTheCountry() {
        List<Map<String, Object>> rows = rows(ShopQueries.countrySpend("IE"));
        Map<Long, Long> paidOrders = byId(rows, "paid_orders");
        Map<Long, Long> spent = byId(rows, "spent_cents");

        assertThat(rows).as("customers in Ireland").hasSize(50);
        assertThat(ids(rows)).as("ordered by id").startsWith(1200L, 1600L, 2000L).endsWith(20_800L);
        assertThat(paidOrders).containsEntry(1200L, 8L).containsEntry(1600L, 8L);
        assertThat(spent).containsEntry(1200L, 100_300L).containsEntry(1600L, 118_300L);
        assertThat(paidOrders).as("customer 2000 never paid: still in the report, with 0").containsEntry(2000L, 0L);
        assertThat(spent).as("customer 2000 never paid: 0, not NULL").containsEntry(2000L, 0L);
        assertThat(paidOrders.values().stream().filter(count -> count == 0)).as("customers who never paid").hasSize(10);
        assertThat(spent.values().stream().mapToLong(Long::longValue).sum()).as("spent by all 50").isEqualTo(4_327_100L);
    }

    @Test
    @DisplayName("Country report: the orders table is read once, not once per customer: no SubPlan in the plan")
    void theOrdersTableIsReadOnce() {
        assertThat(plan.subPlans()).as("subqueries that run once per row: %s", plan).isEmpty();
        assertThat(plan.scans().stream().filter(scan -> scan.relation().equals("orders")).mapToLong(Plan.Node::loops).sum())
                .as("times the orders table was read: %s", plan)
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Country report: Postgres reads no more rows than the two tables hold (320,020)")
    void readsNoMoreRowsThanTheTablesHold() {
        assertThat(plan.rowsRead()).as("rows read to return %d", plan.rowsReturned()).isLessThanOrEqualTo(CUSTOMERS + ORDERS);
    }
}
