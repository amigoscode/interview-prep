package com.amigoscode.indexes;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.indexes.db.Plan;
import com.amigoscode.indexes.shop.ShopQueries;
import com.amigoscode.indexes.support.IntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Task 6: the column is indexed, but the query wraps it in a function, so the index is skipped. */
@Tag("task6")
class SkippedIndexIT extends IntegrationTest {

    @Test
    @DisplayName("Expression index: finding a customer by lower(email) is an Index Scan that reads 1 row, not a Seq Scan")
    void customerByEmailUsesAnIndex() {
        Plan plan = plan(ShopQueries.customerByEmail("CUSTOMER4242@SHOP.TEST"));

        assertThat(plan.rowsReturned()).as("customers found, whatever the case of the email").isEqualTo(1);
        assertThat(plan.has("Seq Scan")).as("plan still has a Seq Scan: %s", plan).isFalse();
        assertThat(plan.rowsRead()).as("rows read to return 1").isEqualTo(1);
    }

    @Test
    @DisplayName("Rewrite: the daily sales report reads only that day's orders through an index, not a Seq Scan")
    void dailySalesUsesAnIndex() {
        LocalDate day = LocalDate.of(2025, 6, 15);
        long ordersThatDay = jdbc.queryForObject(
                "select count(*) from orders where created_at >= ? and created_at < ?", Long.class,
                day.atStartOfDay(), day.plusDays(1).atStartOfDay());

        Plan plan = plan(ShopQueries.dailySales(day));

        assertThat(plan.has("Seq Scan")).as("plan still has a Seq Scan: %s", plan).isFalse();
        assertThat(plan.rowsRead()).as("rows read for a day with %d orders", ordersThatDay).isEqualTo(ordersThatDay);
    }
}
