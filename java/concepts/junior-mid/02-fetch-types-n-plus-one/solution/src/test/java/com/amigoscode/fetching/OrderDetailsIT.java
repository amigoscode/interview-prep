package com.amigoscode.fetching;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.fetching.support.IntegrationTest;
import com.amigoscode.fetching.support.Measured;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

@Tag("task4")
class OrderDetailsIT extends IntegrationTest {

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("Details: GET /api/orders/1 answers 200 with the customer's name and both items, not a LazyInitializationException")
    void orderOneCanBeRead() {
        Measured request = get("/api/orders/1");

        assertThat(request.status()).isEqualTo(200);
        assertThat((String) request.json("$.customerName")).isEqualTo("Customer 1");
        assertThat((List<String>) request.json("$.items[*].product")).containsExactly("Desk lamp", "Notebook");
        assertThat((List<Integer>) request.json("$.items[*].quantity")).containsExactly(2, 3);
    }

    @Test
    @DisplayName("Details: orders 1 to 30 all answer 200, and each total is quantity x price added up over its items")
    void everyOrderHasTheRightTotal() {
        for (long id = 1; id <= 30; id++) {
            BigDecimal expected = jdbc.queryForObject("""
                    select sum(i.quantity * p.price)
                      from order_item i join product p on p.id = i.product_id
                     where i.order_id = ?
                    """, BigDecimal.class, id);

            Measured request = get("/api/orders/" + id);

            assertThat(request.status()).as("status of GET /api/orders/" + id).isEqualTo(200);
            assertThat(new BigDecimal(request.json("$.total").toString())).as("total of order " + id)
                    .isEqualByComparingTo(expected);
        }
    }

    @Test
    @DisplayName("Details: spring.jpa.open-in-view is still false (already passing: keep it that way)")
    void openInViewStaysOff() {
        assertThat(environment.getProperty("spring.jpa.open-in-view"))
                .as("spring.jpa.open-in-view in application.properties")
                .isEqualTo("false");
    }
}
