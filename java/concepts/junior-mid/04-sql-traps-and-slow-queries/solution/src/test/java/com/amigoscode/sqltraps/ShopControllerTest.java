package com.amigoscode.sqltraps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.amigoscode.sqltraps.shop.ShopController;
import com.amigoscode.sqltraps.shop.ShopRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Unit tests for the HTTP contract: no Spring context, no database, ShopRepository is a hand-written
 * stub. They pass from the start and must keep passing.
 */
@Tag("baseline")
class ShopControllerTest {

    @Test
    @DisplayName("HTTP: a report is returned as a JSON array with one object per row")
    void aReportIsAJsonArray() throws Exception {
        StubRepository stub = new StubRepository();

        api(stub).perform(get("/api/reports/never-ordered"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(4))
                .andExpect(jsonPath("$[0].name").value("Margaret"));
    }

    @Test
    @DisplayName("HTTP: /api/orders/recent asks the repository for 20 orders by default, or for ?size=")
    void recentOrdersPassesThePageSize() throws Exception {
        StubRepository stub = new StubRepository();

        api(stub).perform(get("/api/orders/recent")).andExpect(status().isOk());
        api(stub).perform(get("/api/orders/recent?size=5")).andExpect(status().isOk());

        assertThat(stub.pageSizes).containsExactly(20, 5);
    }

    private static MockMvc api(ShopRepository repository) {
        return MockMvcBuilders.standaloneSetup(new ShopController(repository)).build();
    }

    /** Answers without a database, and remembers the page sizes it was asked for. */
    private static final class StubRepository extends ShopRepository {

        private final List<Integer> pageSizes = new ArrayList<>();

        StubRepository() {
            super(null);
        }

        @Override
        public List<Map<String, Object>> customersWhoNeverOrdered() {
            return List.of(Map.of("id", 4, "name", "Margaret"), Map.of("id", 7, "name", "Alan"));
        }

        @Override
        public List<Map<String, Object>> recentOrders(int pageSize) {
            pageSizes.add(pageSize);
            return List.of();
        }
    }
}
