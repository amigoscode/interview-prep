package com.amigoscode.restapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.amigoscode.restapi.order.OrderStatus;
import com.amigoscode.restapi.support.UnitApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit tests: controller + service + exception handler, wired by hand. No Spring context, no database. */
@Tag("task4")
class ErrorMappingTest {

    private final UnitApi api = new UnitApi();

    @Test
    @DisplayName("Errors (unit): an order that does not exist is a 404 that says which order")
    void unknownOrderIsNotFound() throws Exception {
        api.get("/api/orders/999")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Order 999 does not exist"));
    }

    @Test
    @DisplayName("Errors (unit): cancelling a shipped order is a 409 and the order stays SHIPPED")
    void cancellingShippedIsConflict() throws Exception {
        api.post("/api/orders/3/cancel")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        assertThat(api.order(3).getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    @DisplayName("Errors (unit): a body that is not JSON is a 400, not a 500")
    void brokenJsonIsBadRequest() throws Exception {
        api.post("/api/orders", "{\"customer\": \"Nina Patel\", ")
                .andExpect(status().isBadRequest());

        assertThat(api.orders.size()).as("orders stored").isEqualTo(3);
    }
}
