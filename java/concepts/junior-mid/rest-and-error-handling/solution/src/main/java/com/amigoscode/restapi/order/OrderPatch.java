package com.amigoscode.restapi.order;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * The body of a PATCH. Every field is optional: null means "the client did not send it, leave it
 * alone". A field that is sent must still be valid.
 */
public record OrderPatch(
        @Pattern(regexp = ".*\\S.*", message = "must not be blank") String customer,
        Product product,
        @Positive Integer quantity,
        @Pattern(regexp = ".*\\S.*", message = "must not be blank") String shippingAddress,
        @Size(max = 200) String notes,
        @Size(max = 30) String contactPhone) {
}
