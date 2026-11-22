package com.amigoscode.restapi.order;

import java.math.BigDecimal;

/** What a client is allowed to see. The entity's internalNote is not here, so it cannot leak. */
public record OrderResponse(
        long id,
        String customer,
        Product product,
        int quantity,
        BigDecimal total,
        OrderStatus status,
        String shippingAddress,
        String notes,
        String contactPhone) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomer(),
                order.getProduct(),
                order.getQuantity(),
                order.getTotal(),
                order.getStatus(),
                order.getShippingAddress(),
                order.getNotes(),
                order.getContactPhone());
    }
}
