package com.amigoscode.restapi.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * The body of POST /api/orders: what a client may set on a new order.
 * The rules are written down here: a customer, a product, a quantity above zero, an address.
 */
public record OrderRequest(
        @NotBlank String customer,
        @NotNull Product product,
        @Positive int quantity,
        @NotBlank String shippingAddress,
        @Size(max = 200) String notes,
        @Size(max = 30) String contactPhone) {
}
