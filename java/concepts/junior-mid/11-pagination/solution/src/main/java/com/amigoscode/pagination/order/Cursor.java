package com.amigoscode.pagination.order;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;

/**
 * Where a page of the feed ended: the sort key of its last row. The next page is "everything that
 * sorts after this".
 *
 * On the wire it is opaque: Base64 of "createdAt|id". Clients send it back unchanged and never
 * build one themselves, so the format can change without breaking them. Base64 is an encoding, not
 * a secret: anyone can decode it, which is fine, because it holds nothing the client has not seen.
 */
public record Cursor(Instant createdAt, long id) {

    public static Cursor after(Order lastRow) {
        return new Cursor(lastRow.getCreatedAt(), lastRow.getId());
    }

    public String encode() {
        String text = createdAt + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    /** Reads a cursor a client sent. Anything that is not a cursor is the client's mistake: a 400. */
    public static Cursor decode(String encoded) {
        try {
            String text = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            int separator = text.indexOf('|');
            if (separator < 0) {
                throw new InvalidCursorException();
            }
            Instant createdAt = Instant.parse(text.substring(0, separator));
            long id = Long.parseLong(text.substring(separator + 1));
            return new Cursor(createdAt, id);
        } catch (IllegalArgumentException | DateTimeException e) {
            // Not Base64, not a timestamp or not a number.
            throw new InvalidCursorException();
        }
    }
}
