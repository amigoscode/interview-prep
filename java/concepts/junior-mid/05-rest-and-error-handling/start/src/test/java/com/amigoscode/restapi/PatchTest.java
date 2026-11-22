package com.amigoscode.restapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.amigoscode.restapi.order.Order;
import com.amigoscode.restapi.order.OrderStatus;
import com.amigoscode.restapi.order.Product;
import com.amigoscode.restapi.support.UnitApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Unit tests for PATCH. No Spring context, no database. */
@Tag("task8")
class PatchTest {

    private final UnitApi api = new UnitApi();

    @Test
    @DisplayName("PATCH (unit): only the field that was sent changes")
    void onlyWhatIsSentChanges() throws Exception {
        api.patch("/api/orders/1", "{\"notes\": \"Leave it with the neighbour\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("Leave it with the neighbour"))
                .andExpect(jsonPath("$.customer").value("Maria Jones"));

        Order order = api.order(1);
        assertThat(order.getNotes()).isEqualTo("Leave it with the neighbour");
        assertThat(order.getCustomer()).isEqualTo("Maria Jones");
        assertThat(order.getProduct()).isEqualTo(Product.MONITOR);
        assertThat(order.getQuantity()).isEqualTo(1);
        assertThat(order.getShippingAddress()).isEqualTo("12 Mill Lane, Leeds");
        assertThat(order.getContactPhone()).isEqualTo("0113 496 0001");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getInternalNote()).isEqualTo("Card was declined once in May");
    }

    @Test
    @DisplayName("PATCH (unit): a new quantity changes the total, because the server works the total out")
    void quantityChangesTheTotal() throws Exception {
        api.patch("/api/orders/1", "{\"quantity\": 3}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.total").value(600.00));

        assertThat(api.order(1).getTotal()).isEqualByComparingTo("600.00");
    }

    @Test
    @DisplayName("PATCH (unit): a field that is sent must be valid: quantity 0 is a 400 and nothing changes")
    void whatIsSentMustBeValid() throws Exception {
        api.patch("/api/orders/1", "{\"quantity\": 0, \"notes\": \"Leave it with the neighbour\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("quantity"));

        assertThat(api.order(1).getQuantity()).as("quantity").isEqualTo(1);
        assertThat(api.order(1).getNotes()).as("notes").isEqualTo("Ring the bell twice");
    }
}
