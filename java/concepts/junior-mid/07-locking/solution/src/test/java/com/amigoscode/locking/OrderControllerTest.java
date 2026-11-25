package com.amigoscode.locking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.amigoscode.locking.order.Order;
import com.amigoscode.locking.order.OrderConflictException;
import com.amigoscode.locking.order.OrderController;
import com.amigoscode.locking.order.OrderService;
import com.amigoscode.locking.order.SoldOutException;
import com.amigoscode.locking.order.Strategy;
import com.amigoscode.locking.product.ProductNotFoundException;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Unit tests for the HTTP contract: no Spring context, no database, OrderService is a stub. They
 * pass from the start and must keep passing: whatever you change in the service, a buyer who loses
 * must get a clear 409, never a 500.
 */
@Tag("baseline")
class OrderControllerTest {

    private static final String BODY = "{\"productId\":1,\"quantity\":1}";

    @Test
    @DisplayName("HTTP: a placed order returns 201 with the order id")
    void placedOrderReturnsCreated() throws Exception {
        Order order = new Order(1, 1);
        ReflectionTestUtils.setField(order, "id", 42L);

        api(() -> order).perform(post("/api/orders?strategy=pessimistic").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(42))
                .andExpect(jsonPath("$.productId").value(1));
    }

    @Test
    @DisplayName("HTTP: sold out returns 409 SOLD_OUT")
    void soldOutReturnsConflict() throws Exception {
        api(() -> {
            throw new SoldOutException(1);
        }).perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SOLD_OUT"));
    }

    @Test
    @DisplayName("HTTP: giving up after retries returns 409 ORDER_CONFLICT")
    void givingUpReturnsConflict() throws Exception {
        api(() -> {
            throw new OrderConflictException(1, 3);
        }).perform(post("/api/orders?strategy=optimistic").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_CONFLICT"));
    }

    @Test
    @DisplayName("HTTP: an unknown product returns 404")
    void unknownProductReturnsNotFound() throws Exception {
        api(() -> {
            throw new ProductNotFoundException(1);
        }).perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("HTTP: an unknown strategy returns 400")
    void unknownStrategyReturnsBadRequest() throws Exception {
        api(() -> new Order(1, 1)).perform(post("/api/orders?strategy=hope").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
    }

    /** The controller wired to an OrderService whose placeOrder does whatever the test says. */
    private static MockMvc api(Supplier<Order> placeOrder) {
        OrderService stub = new OrderService(null) {
            @Override
            public Order placeOrder(Strategy strategy, long productId, int quantity) {
                return placeOrder.get();
            }
        };
        return MockMvcBuilders.standaloneSetup(new OrderController(stub))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }
}
