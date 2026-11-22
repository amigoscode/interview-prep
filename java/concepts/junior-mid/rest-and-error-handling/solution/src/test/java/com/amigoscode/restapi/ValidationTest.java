package com.amigoscode.restapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.amigoscode.restapi.support.UnitApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit tests for the rules on OrderRequest, through the controller. No Spring context, no database. */
@Tag("task5")
class ValidationTest {

    private final UnitApi api = new UnitApi();

    @Test
    @DisplayName("Validation (unit): a blank customer is a 400 that names the field \"customer\"")
    void blankCustomerIsRejected() throws Exception {
        api.post("/api/orders", UnitApi.VALID_ORDER.replace("Nina Patel", "  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("customer"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());

        assertThat(api.orders.size()).as("orders stored").isEqualTo(3);
    }

    @Test
    @DisplayName("Validation (unit): quantity 0 and quantity -5 are both a 400 that names the field \"quantity\"")
    void quantityMustBePositive() throws Exception {
        for (String quantity : new String[] {"0", "-5"}) {
            api.post("/api/orders", UnitApi.VALID_ORDER.replace("\"quantity\": 2", "\"quantity\": " + quantity))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("quantity"));
        }

        assertThat(api.orders.size()).as("orders stored").isEqualTo(3);
    }

    @Test
    @DisplayName("Validation (unit): a missing product is a 400 that names the field \"product\"")
    void productIsRequired() throws Exception {
        api.post("/api/orders", UnitApi.VALID_ORDER.replace("\"product\": \"MOUSE\", ", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("product"));

        assertThat(api.orders.size()).as("orders stored").isEqualTo(3);
    }

    @Test
    @DisplayName("Validation (unit): a valid order is still accepted")
    void validOrderIsAccepted() throws Exception {
        api.post("/api/orders", UnitApi.VALID_ORDER).andExpect(status().is2xxSuccessful());

        assertThat(api.orders.size()).as("orders stored").isEqualTo(4);
    }
}
