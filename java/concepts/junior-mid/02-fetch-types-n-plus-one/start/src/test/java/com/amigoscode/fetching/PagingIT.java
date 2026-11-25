package com.amigoscode.fetching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.amigoscode.fetching.customer.Customer;
import com.amigoscode.fetching.support.IntegrationTest;
import com.amigoscode.fetching.support.Measured;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Task 9 (mid-level): a page of orders, each with its items. */
@Tag("task9")
class PagingIT extends IntegrationTest {

    @Test
    @DisplayName("Paging: page 2 of size 20 holds orders 41 to 60 out of 200, each with its own items and total")
    void pageHoldsTheRightOrders() {
        Measured request = get("/api/orders/page?page=2&size=20");

        assertThat(request.status()).isEqualTo(200);
        assertThat((int) request.json("$.totalOrders")).isEqualTo(ORDERS);
        assertThat((List<Integer>) request.json("$.orders[*].id"))
                .containsExactlyElementsOf(IntStream.rangeClosed(41, 60).boxed().toList());
        for (int i = 0; i < 20; i++) {
            long id = 41 + i;
            Integer items = jdbc.queryForObject("select count(*) from order_item where order_id = ?", Integer.class, id);
            BigDecimal total = jdbc.queryForObject("""
                    select sum(i.quantity * p.price)
                      from order_item i join product p on p.id = i.product_id
                     where i.order_id = ?
                    """, BigDecimal.class, id);
            assertThat((int) request.json("$.orders[" + i + "].items.length()")).as("items of order " + id).isEqualTo(items);
            assertThat(new BigDecimal(request.json("$.orders[" + i + "].total").toString())).as("total of order " + id)
                    .isEqualByComparingTo(total);
        }
    }

    @Test
    @DisplayName("Paging: the database cuts the page: the statement that selects the orders has a limit (fetch first ... rows only)")
    void theDatabaseDoesThePaging() {
        Measured request = get("/api/orders/page?page=2&size=20");

        assertThat(request.status()).isEqualTo(200);
        assertThat(request.statementsFrom("orders")).as(request.describe())
                .anyMatch(sql -> {
                    String lower = sql.toLowerCase(Locale.ROOT);
                    return lower.contains(" fetch first ") || lower.contains(" limit ");
                });
    }

    @Test
    @DisplayName("Paging: a page of 20 orders with their items runs at most 5 statements")
    void aPageRunsAHandfulOfStatements() {
        Measured request = get("/api/orders/page?page=2&size=20");

        assertThat(request.status()).isEqualTo(200);
        assertThat(request.statementCount()).as(request.describe()).isLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("Two bags: fetching Customer.orders and Order.items in one query is refused with MultipleBagFetchException (this documents the limit)")
    void twoBagsCannotBeFetchedTogether() {
        assertThatThrownBy(() -> inTransaction(() -> entityManager
                .createQuery("select c from Customer c join fetch c.orders o join fetch o.items", Customer.class)
                .getResultList()))
                .hasStackTraceContaining("MultipleBagFetchException")
                .hasStackTraceContaining("cannot simultaneously fetch multiple bags");
    }
}
