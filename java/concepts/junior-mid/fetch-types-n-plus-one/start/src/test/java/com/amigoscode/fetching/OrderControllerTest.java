package com.amigoscode.fetching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.amigoscode.fetching.order.FetchPlan;
import com.amigoscode.fetching.order.OrderController;
import com.amigoscode.fetching.order.OrderDetails;
import com.amigoscode.fetching.order.OrderNotFoundException;
import com.amigoscode.fetching.order.OrderService;
import com.amigoscode.fetching.order.OrderStatus;
import com.amigoscode.fetching.order.OrderSummary;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Unit tests for the HTTP contract: no Spring context, no database, OrderService is a hand-written
 * stub. They pass from the start and must keep passing: however you load the orders, the JSON the
 * client gets stays the same.
 */
@Tag("baseline")
class OrderControllerTest {

    private static final OrderSummary SUMMARY =
            new OrderSummary(7, OrderStatus.PAID, Instant.parse("2026-01-01T09:07:00Z"), "Ada", "London");

    @Test
    @DisplayName("HTTP: GET /api/orders returns the order list as JSON and uses the naive fetch plan by default")
    void listReturnsSummaries() throws Exception {
        StubOrderService service = new StubOrderService();

        api(service).perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].status").value("PAID"))
                .andExpect(jsonPath("$[0].customerName").value("Ada"))
                .andExpect(jsonPath("$[0].customerCity").value("London"));

        assertThat(service.plans).containsExactly(FetchPlan.NAIVE);
    }

    @Test
    @DisplayName("HTTP: ?fetch=join-fetch, entity-graph and projection pick the matching fetch plan")
    void fetchParameterPicksThePlan() throws Exception {
        StubOrderService service = new StubOrderService();
        MockMvc api = api(service);

        api.perform(get("/api/orders?fetch=join-fetch")).andExpect(status().isOk());
        api.perform(get("/api/orders?fetch=entity-graph")).andExpect(status().isOk());
        api.perform(get("/api/orders?fetch=projection")).andExpect(status().isOk());

        assertThat(service.plans).containsExactly(FetchPlan.JOIN_FETCH, FetchPlan.ENTITY_GRAPH, FetchPlan.PROJECTION);
    }

    @Test
    @DisplayName("HTTP: an unknown fetch plan returns 400")
    void unknownFetchPlanReturnsBadRequest() throws Exception {
        api(new StubOrderService()).perform(get("/api/orders?fetch=hope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("HTTP: an unknown order returns 404 ORDER_NOT_FOUND")
    void unknownOrderReturnsNotFound() throws Exception {
        api(new StubOrderService()).perform(get("/api/orders/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    /** An OrderService that never touches a database: it remembers the plan and returns one fixed row. */
    private static final class StubOrderService extends OrderService {

        private final List<FetchPlan> plans = new ArrayList<>();

        StubOrderService() {
            super(null);
        }

        @Override
        public List<OrderSummary> list(FetchPlan plan) {
            plans.add(plan);
            return List.of(SUMMARY);
        }

        @Override
        public OrderDetails details(long id) {
            throw new OrderNotFoundException(id);
        }
    }

    private static MockMvc api(OrderService service) {
        return MockMvcBuilders.standaloneSetup(new OrderController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }
}
