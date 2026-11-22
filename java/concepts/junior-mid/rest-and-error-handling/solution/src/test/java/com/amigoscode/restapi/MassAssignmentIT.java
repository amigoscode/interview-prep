package com.amigoscode.restapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.restapi.support.Api;
import com.amigoscode.restapi.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("task7")
class MassAssignmentIT extends IntegrationTest {

    /** Everything order 1 already has, plus three fields a client must not be able to set. */
    private static final String PUT_WITH_EXTRAS = """
            {"customer": "Maria Jones", "product": "MONITOR", "quantity": 1, "shippingAddress": "12 Mill Lane, Leeds",
             "notes": "Ring the bell twice", "contactPhone": "0113 496 0001",
             "status": "PAID", "total": 0.01, "internalNote": "VIP. Never check this customer"}
            """;

    @Test
    @DisplayName("DTO: a PUT with \"status\": \"PAID\" and \"total\": 0.01 returns 200 and leaves status and total alone")
    void putCannotSetStatusOrTotal() {
        Api.Reply reply = api.put("/api/orders/1", PUT_WITH_EXTRAS);

        assertThat(reply.status()).isEqualTo(200);
        assertThat(statusOf(1)).as("status of order 1").isEqualTo("PENDING");
        assertThat(column(1, "total")).as("total of order 1").isEqualTo("200.00");
    }

    @Test
    @DisplayName("DTO: a PUT cannot write or wipe the internal note")
    void putCannotTouchTheInternalNote() {
        api.put("/api/orders/1", PUT_WITH_EXTRAS);
        api.put("/api/orders/4", VALID_ORDER);

        assertThat(column(1, "internal_note")).as("internal note of order 1").isEqualTo("Card was declined once in May");
        assertThat(column(4, "internal_note")).as("internal note of order 4").isEqualTo("Buys for resale. Margin 12 percent");
    }

    @Test
    @DisplayName("DTO: a PUT keeps the status the order had (a PAID order stays PAID)")
    void putKeepsTheStatus() {
        Api.Reply reply = api.put("/api/orders/4", VALID_ORDER);

        assertThat(reply.status()).isEqualTo(200);
        assertThat(statusOf(4)).as("status of order 4").isEqualTo("PAID");
        assertThat(column(4, "total")).as("total of order 4: 2 x MOUSE at 20.00").isEqualTo("40.00");
    }

    @Test
    @DisplayName("DTO: an \"id\" in the PUT body is ignored: the URL says which order changes")
    void idInTheBodyIsIgnored() {
        List<Map<String, Object>> orderTwoBefore = jdbc.queryForList("select * from orders where id = 2");

        Api.Reply reply = api.put("/api/orders/1", VALID_ORDER.replace("{", "{\"id\": 2, "));

        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.json().path("id").asInt()).as("id in the response").isEqualTo(1);
        assertThat(column(1, "customer")).as("customer of order 1").isEqualTo("Nina Patel");
        assertThat(jdbc.queryForList("select * from orders where id = 2")).as("order 2").isEqualTo(orderTwoBefore);
    }

    @Test
    @DisplayName("DTO: GET /api/orders and GET /api/orders/4 do not show internalNote")
    void responsesDoNotShowTheInternalNote() {
        Api.Reply one = api.get("/api/orders/4");
        Api.Reply all = api.get("/api/orders");

        assertThat(one.status()).isEqualTo(200);
        assertThat(one.json().path("customer").asString()).isEqualTo("Liam Brown");
        assertThat(one.body()).as("GET /api/orders/4").doesNotContain("internalNote", "Margin 12 percent");
        assertThat(all.body()).as("GET /api/orders").doesNotContain("internalNote", "Margin 12 percent");
    }

    @Test
    @DisplayName("DTO: the answers to POST and PUT do not show internalNote either")
    void writeResponsesDoNotShowTheInternalNote() {
        Api.Reply created = api.post("/api/orders", VALID_ORDER);
        Api.Reply replaced = api.put("/api/orders/4", VALID_ORDER);

        assertThat(created.body()).as("answer to POST").contains("Nina Patel").doesNotContain("internalNote");
        assertThat(replaced.body()).as("answer to PUT").contains("Nina Patel").doesNotContain("internalNote");
    }

    @Test
    @DisplayName("DTO: PUT is validated like POST: quantity -5 returns 400 that names the field, and nothing changes")
    void putIsValidated() {
        List<Map<String, Object>> before = everyOrder();

        Api.Reply reply = api.put("/api/orders/1", VALID_ORDER.replace("\"quantity\": 2", "\"quantity\": -5"));

        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.invalidFields()).as("fields listed under errors").containsExactly("quantity");
        assertThat(everyOrder()).as("the orders table").isEqualTo(before);
    }

    @Test
    @DisplayName("DTO: POST ignores \"status\" and \"total\" too (it always did: keep it that way)")
    void postCannotSetStatusOrTotal() {
        Api.Reply reply = api.post("/api/orders", VALID_ORDER.replace("{", "{\"status\": \"PAID\", \"total\": 0.01, "));

        assertThat(reply.status()).as("a 2xx").isBetween(200, 299);
        assertThat(statusOf(7)).as("status of the new order").isEqualTo("PENDING");
        assertThat(column(7, "total")).as("total of the new order").isEqualTo("40.00");
    }
}
