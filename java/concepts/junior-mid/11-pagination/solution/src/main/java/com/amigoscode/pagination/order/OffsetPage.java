package com.amigoscode.pagination.order;

import java.util.List;

/**
 * One page of the offset endpoints: GET /api/orders?page=3&size=20.
 *
 * @param page    the page number the client asked for, starting at 0
 * @param hasNext true when there is a page after this one. The screens only have a "Next" button
 */
public record OffsetPage(List<OrderResponse> items, int page, int size, boolean hasNext) {
}
