package com.amigoscode.restapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.amigoscode.restapi.support.UnitApi;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit test for the answer to a create. No Spring context, no database. */
@Tag("task10")
class CreatedTest {

    private final UnitApi api = new UnitApi();

    @Test
    @DisplayName("Status (unit): POST answers 201, with the new order in the body and its URL in Location")
    void createAnswersCreatedWithLocation() throws Exception {
        api.post("/api/orders", UnitApi.VALID_ORDER)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(4))
                .andExpect(header().string("Location", Matchers.endsWith("/api/orders/4")));

        assertThat(api.orders.size()).as("orders stored").isEqualTo(4);
    }
}
