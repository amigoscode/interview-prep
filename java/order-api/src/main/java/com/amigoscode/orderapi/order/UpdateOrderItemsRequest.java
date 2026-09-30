package com.amigoscode.orderapi.order;

import java.util.List;

/** Body of {@code PUT /orders/{id}}: the full replacement list of items. */
public record UpdateOrderItemsRequest(List<OrderItemRequest> items) {
}
