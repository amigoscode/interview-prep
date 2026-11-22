package com.amigoscode.indexes;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.amigoscode.indexes.shop.ShopController;
import com.amigoscode.indexes.shop.ShopRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Unit tests for the HTTP contract: no Spring context, no database, ShopRepository is a stub. They
 * pass from the start and must keep passing: an index changes how fast an answer is found, never
 * what the answer is.
 */
@Tag("baseline")
class ShopControllerTest {

    @Test
    @DisplayName("HTTP: tracking an order returns 200 with the order")
    void trackedOrderIsReturned() throws Exception {
        api(Optional.of(Map.of("id", 42, "status", "SHIPPED")))
                .perform(get("/api/orders/track/TRK123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }

    @Test
    @DisplayName("HTTP: an unknown tracking number returns 404")
    void unknownTrackingNumberIsNotFound() throws Exception {
        api(Optional.empty())
                .perform(get("/api/orders/track/NOPE"))
                .andExpect(status().isNotFound());
    }

    /** The controller wired to a ShopRepository whose trackOrder returns whatever the test says. */
    private static MockMvc api(Optional<Map<String, Object>> order) {
        ShopRepository stub = new ShopRepository(null) {
            @Override
            public Optional<Map<String, Object>> trackOrder(String trackingNumber) {
                return order;
            }
        };
        return MockMvcBuilders.standaloneSetup(new ShopController(stub)).build();
    }
}
