package com.amigoscode.orderapi.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Body of {@code PUT /orders/{id}}: the full replacement list of items. */
public record UpdateOrderItemsRequest(@NotEmpty List<@Valid @NotNull OrderItemRequest> items) {
}
