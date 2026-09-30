package com.amigoscode.orderapi.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Body of {@code POST /orders}. There is no total: the server computes it. */
public record CreateOrderRequest(
        @NotBlank String customerId,
        @NotBlank String restaurantId,
        @NotEmpty List<@Valid @NotNull OrderItemRequest> items) {
}
