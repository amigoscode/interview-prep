package com.amigoscode.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.amigoscode.pagination.support.StubOrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the HTTP contract: no Spring context, no database, OrderService is a stub. They
 * pass from the start and must keep passing, whatever you change behind the controller.
 */
@Tag("baseline")
class OrderControllerTest {

    private final StubOrderService service = new StubOrderService();

    @Test
    @DisplayName("HTTP: GET /api/orders?page=3&size=10 returns items, page, size and hasNext")
    void aPageOfOrders() throws Exception {
        service.api().perform(get("/api/orders?page=3&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(42))
                .andExpect(jsonPath("$.items[0].customerId").value(7))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.page").value(3))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.hasNext").value(true));

        assertThat(service.page).as("page passed to the service").isEqualTo(3);
        assertThat(service.size).as("size passed to the service").isEqualTo(10);
    }

    @Test
    @DisplayName("HTTP: GET /api/customers/7/orders returns that customer's orders")
    void aCustomersHistory() throws Exception {
        service.api().perform(get("/api/customers/7/orders?page=1&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(42))
                .andExpect(jsonPath("$.hasNext").value(false));

        assertThat(service.customerId).as("customer id passed to the service").isEqualTo(7);
        assertThat(service.page).as("page passed to the service").isEqualTo(1);
    }

    @Test
    @DisplayName("HTTP: GET /api/orders/feed returns items, nextCursor and hasNext")
    void aPageOfTheFeed() throws Exception {
        service.api().perform(get("/api/orders/feed?size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(42))
                .andExpect(jsonPath("$.nextCursor").value("next-cursor"))
                .andExpect(jsonPath("$.hasNext").value(true));

        assertThat(service.after).as("cursor passed to the service when the client sent none").isNull();
    }

    @Test
    @DisplayName("HTTP: a size that is not a number returns 400")
    void sizeMustBeANumber() throws Exception {
        service.api().perform(get("/api/orders?size=lots"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        assertThat(service.calls).as("calls that reached the service").isZero();
    }
}
