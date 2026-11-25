package com.amigoscode.pagination.order;

import java.util.List;

/**
 * One page of the keyset endpoint: GET /api/orders/feed?after=...&size=20.
 *
 * @param nextCursor send it back as "after" to get the next page. null on the last page
 */
public record FeedPage(List<OrderResponse> items, String nextCursor, boolean hasNext) {
}
