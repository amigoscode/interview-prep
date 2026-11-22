package com.amigoscode.restapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.amigoscode.restapi.support.Api;
import com.amigoscode.restapi.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("task2")
class CancelIT extends IntegrationTest {

    @Test
    @DisplayName("Cancel: GET /api/orders/1/cancel is refused and the order stays PENDING")
    void getDoesNotCancel() {
        Api.Reply reply = api.get("/api/orders/1/cancel");

        assertThat(statusOf(1)).as("order 1 after a GET").isEqualTo("PENDING");
        assertThat(reply.status()).as("status code of the GET (it must not be a success)").isGreaterThanOrEqualTo(400);
    }

    @Test
    @DisplayName("Cancel: POST /api/orders/1/cancel returns 200 and the order is CANCELLED")
    void postCancelsTheOrder() {
        Api.Reply reply = api.send("POST", "/api/orders/1/cancel", null);

        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.json().path("status").asString()).as("status in the response").isEqualTo("CANCELLED");
        assertThat(statusOf(1)).as("order 1 in the database").isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("Cancel: the same POST sent twice is safe: 200 both times, still CANCELLED, nothing else changed")
    void cancellingTwiceIsSafe() {
        Api.Reply first = api.send("POST", "/api/orders/2/cancel", null);
        List<Map<String, Object>> afterFirst = everyOrder();
        Api.Reply second = api.send("POST", "/api/orders/2/cancel", null);

        assertThat(first.status()).as("first cancel").isEqualTo(200);
        assertThat(second.status()).as("second cancel").isEqualTo(200);
        assertThat(statusOf(2)).isEqualTo("CANCELLED");
        assertThat(everyOrder()).as("the table after the second cancel").isEqualTo(afterFirst);
    }

    @Test
    @DisplayName("Cancel: requesting every link on the admin page changes no order")
    void followingEveryLinkChangesNothing() {
        List<Map<String, Object>> before = everyOrder();
        Api.Reply page = api.get("/admin/pending-orders");
        assertThat(page.status()).isEqualTo(200);

        Matcher link = Pattern.compile("href=\"([^\"]+)\"").matcher(page.body());
        while (link.find()) {
            api.get(link.group(1));
        }

        assertThat(everyOrder()).as("the orders table after a link checker visited the page").isEqualTo(before);
    }

    @Test
    @DisplayName("Cancel: the admin page cancels with a form that POSTs, not with a link")
    void adminPageUsesAPostForm() {
        String page = api.get("/admin/pending-orders").body().toLowerCase();

        assertThat(page).as("the admin page").doesNotContainPattern("href=\"[^\"]*cancel");
        assertThat(page).as("the admin page").containsPattern("<form[^>]*method=\"post\"");
        assertThat(page).as("the admin page").containsPattern("<form[^>]*action=\"/api/orders/1/cancel\"");
    }
}
