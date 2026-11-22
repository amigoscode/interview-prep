package com.amigoscode.restapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.amigoscode.restapi.order.Order;
import com.amigoscode.restapi.order.OrderStatus;
import com.amigoscode.restapi.support.UnitApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit tests for what goes in and what comes out. No Spring context, no database. */
@Tag("task7")
class DtoTest {

    private static final String PUT_WITH_EXTRAS = """
            {"customer": "Maria Jones", "product": "MONITOR", "quantity": 1, "shippingAddress": "12 Mill Lane, Leeds",
             "id": 2, "status": "PAID", "total": 0.01, "internalNote": "VIP"}
            """;

    private final UnitApi api = new UnitApi();

    @Test
    @DisplayName("DTO (unit): a response has id, customer, product, quantity, total and status, and no internalNote")
    void responseHasNoInternalNote() throws Exception {
        api.get("/api/orders/1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.customer").value("Maria Jones"))
                .andExpect(jsonPath("$.product").value("MONITOR"))
                .andExpect(jsonPath("$.quantity").value(1))
                .andExpect(jsonPath("$.total").value(200.00))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.notes").value("Ring the bell twice"))
                .andExpect(jsonPath("$.internalNote").doesNotExist());
        api.get("/api/orders")
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].internalNote").doesNotExist());
    }

    @Test
    @DisplayName("DTO (unit): status, total, id and internalNote in a PUT body are ignored")
    void putIgnoresWhatTheClientMayNotSet() throws Exception {
        api.put("/api/orders/1", PUT_WITH_EXTRAS)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.internalNote").doesNotExist());

        Order order = api.order(1);
        assertThat(order.getStatus()).as("status").isEqualTo(OrderStatus.PENDING);
        assertThat(order.getTotal()).as("total").isEqualByComparingTo("200.00");
        assertThat(order.getInternalNote()).as("internal note").isEqualTo("Card was declined once in May");
        assertThat(api.order(2).getCustomer()).as("customer of order 2, the id in the body").isEqualTo("Liam Brown");
    }
}
