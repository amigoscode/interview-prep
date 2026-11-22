package com.amigoscode.restapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.restapi.support.Api;
import com.amigoscode.restapi.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("task8")
class PutPatchIT extends IntegrationTest {

    @Test
    @DisplayName("PUT: a field left out of the body is cleared, because PUT replaces the whole order (it always did)")
    void putClearsWhatIsLeftOut() {
        Api.Reply reply = api.put("/api/orders/4", """
                {"customer": "Liam Brown", "product": "MONITOR", "quantity": 2, "shippingAddress": "1 New Street, Cardiff"}
                """);

        assertThat(reply.status()).isEqualTo(200);
        assertThat(column(4, "shipping_address")).isEqualTo("1 New Street, Cardiff");
        assertThat(column(4, "notes")).as("notes, not in the body").isNull();
        assertThat(column(4, "contact_phone")).as("contact phone, not in the body").isNull();
    }

    @Test
    @DisplayName("PATCH: sending only shippingAddress returns 200, changes the address and nothing else")
    void patchChangesOnlyTheAddress() {
        Api.Reply reply = api.patch("/api/orders/4", "{\"shippingAddress\": \"1 New Street, Cardiff\"}");

        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.json().path("shippingAddress").asString()).isEqualTo("1 New Street, Cardiff");
        assertThat(reply.json().path("notes").asString()).as("notes in the response").isEqualTo("Gift. No invoice in the box");

        Map<String, Object> row = jdbc.queryForMap("select * from orders where id = 4");
        assertThat(row).containsEntry("shipping_address", "1 New Street, Cardiff")
                .containsEntry("customer", "Liam Brown")
                .containsEntry("product", "MONITOR")
                .containsEntry("quantity", 2)
                .containsEntry("status", "PAID")
                .containsEntry("notes", "Gift. No invoice in the box")
                .containsEntry("contact_phone", "029 2018 0002")
                .containsEntry("internal_note", "Buys for resale. Margin 12 percent");
        assertThat(column(4, "total")).isEqualTo("400.00");
    }

    @Test
    @DisplayName("PATCH: status, total and internalNote cannot be changed")
    void patchCannotSetProtectedFields() {
        List<Map<String, Object>> before = everyOrder();

        Api.Reply reply = api.patch("/api/orders/1", "{\"status\": \"PAID\", \"total\": 0.01, \"internalNote\": \"VIP\"}");

        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.body()).doesNotContain("internalNote");
        assertThat(everyOrder()).as("the orders table").isEqualTo(before);
    }

    @Test
    @DisplayName("PATCH: an order that does not exist returns 404")
    void patchOfMissingOrderIsNotFound() {
        Api.Reply reply = api.patch("/api/orders/999", "{\"notes\": \"Leave it with the neighbour\"}");

        assertThat(reply.status()).isEqualTo(404);
        assertThat(reply.contentType()).isEqualTo("application/problem+json");
    }

    @Test
    @DisplayName("PATCH: a blank customer returns 400 that names the field, and nothing changes")
    void patchIsValidated() {
        List<Map<String, Object>> before = everyOrder();

        Api.Reply reply = api.patch("/api/orders/1", "{\"customer\": \"  \", \"notes\": \"Leave it with the neighbour\"}");

        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.invalidFields()).as("fields listed under errors").containsExactly("customer");
        assertThat(everyOrder()).as("the orders table").isEqualTo(before);
    }
}
