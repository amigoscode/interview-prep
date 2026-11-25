package com.amigoscode.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.amigoscode.pagination.order.Cursor;
import com.amigoscode.pagination.order.InvalidCursorException;
import com.amigoscode.pagination.support.StubOrderService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Task 6: the cursor is opaque to the client, and a cursor the API cannot read is a 400. Unit tests. */
@Tag("task6")
class CursorTest {

    private static final Cursor CURSOR = new Cursor(Instant.parse("2025-11-25T10:00:00.123456Z"), 987_654);

    private final StubOrderService service = new StubOrderService();

    @Test
    @DisplayName("Cursor: decode(encode(cursor)) gives the same cursor back, to the microsecond")
    void roundTrip() {
        assertThat(Cursor.decode(CURSOR.encode())).isEqualTo(CURSOR);
    }

    @Test
    @DisplayName("Cursor: the encoded form is URL-safe Base64, with no timestamp or id readable in it")
    void theEncodedFormIsOpaque() {
        String encoded = CURSOR.encode();

        assertThat(encoded).as("encoded cursor").matches("[A-Za-z0-9_-]+");
        assertThat(encoded).as("encoded cursor").doesNotContain("2025-11-25").doesNotContain("987654");
        assertThat(new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8))
                .as("what the Base64 text decodes to")
                .contains("2025-11-25T10:00:00.123456Z")
                .contains("987654");
    }

    @Test
    @DisplayName("Cursor: text that is not a cursor is an InvalidCursorException, never another exception")
    void garbageIsRejected() {
        List<String> notCursors = List.of(
                "not-a-cursor",
                "!!!",
                "2025-11-25T10:00:00Z|987654",
                base64("hello"),
                base64("2025-11-25T10:00:00Z"),
                base64("2025-11-25T10:00:00Z|"),
                base64("2025-11-25T10:00:00Z|abc"),
                base64("yesterday|987654"),
                base64("|"));

        for (String text : notCursors) {
            assertThatThrownBy(() -> Cursor.decode(text))
                    .as("Cursor.decode(\"%s\")", text)
                    .isInstanceOf(InvalidCursorException.class);
        }
    }

    @Test
    @DisplayName("HTTP: a malformed cursor returns 400 INVALID_CURSOR, not a 500")
    void aMalformedCursorIsABadRequest() throws Exception {
        for (String after : new String[] {"not-a-cursor", "%7C", base64("yesterday|987654")}) {
            service.api().perform(get("/api/orders/feed?after=" + after))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        }

        assertThat(service.calls).as("calls that reached the service").isZero();
    }

    @Test
    @DisplayName("HTTP: a cursor the API handed out is accepted and reaches the service")
    void aValidCursorReachesTheService() throws Exception {
        service.api().perform(get("/api/orders/feed").param("after", CURSOR.encode()))
                .andExpect(status().isOk());

        assertThat(service.after).as("cursor passed to the service").isEqualTo(CURSOR);
    }

    private static String base64(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
