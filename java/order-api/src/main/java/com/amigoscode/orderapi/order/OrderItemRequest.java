package com.amigoscode.orderapi.order;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * One line of an incoming order. Boxed types so a missing field arrives as {@code null} and fails
 * validation instead of silently becoming {@code 0}.
 */
public record OrderItemRequest(
        @NotBlank String name,
        @NotNull @Min(1) @Max(50) Integer quantity,
        @NotNull @Positive Long priceCents) {

    OrderItem toItem() {
        return new OrderItem(name, quantity, priceCents);
    }
}
