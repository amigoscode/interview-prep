package com.amigoscode.pagination.order;

import java.time.Instant;

/**
 * Where a page of the feed ended: the sort key of its last row. The next page is "everything that
 * sorts after this".
 *
 * On the wire it is the two values with a | between them: 2025-11-25T10:00:00Z|999981
 */
public record Cursor(Instant createdAt, long id) {

    public static Cursor after(Order lastRow) {
        return new Cursor(lastRow.getCreatedAt(), lastRow.getId());
    }

    public String encode() {
        return createdAt + "|" + id;
    }

    /** Reads a cursor a client sent. */
    public static Cursor decode(String encoded) {
        String[] parts = encoded.split("\\|");
        return new Cursor(Instant.parse(parts[0]), Long.parseLong(parts[1]));
    }
}
