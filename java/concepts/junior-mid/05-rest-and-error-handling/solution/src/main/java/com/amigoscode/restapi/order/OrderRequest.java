package com.amigoscode.restapi.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Everything a client may set on an order, and nothing else. There is no id, no status, no total
 * and no internalNote here, so a client cannot set them however hard it tries.
 * Used by POST (create) and PUT (replace).
 */
public record OrderRequest(
        @NotBlank String customer,
        @NotNull Product product,
        @Positive int quantity,
        @NotBlank String shippingAddress,
        @Size(max = 200) String notes,
        @Size(max = 30) String contactPhone) {
}
