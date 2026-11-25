package com.amigoscode.fetching.order;

import java.math.BigDecimal;
import java.util.List;

/** One order with its customer and its items. Reads the customer, the items and every item's product. */
public record OrderDetails(long id, OrderStatus status, String customerName, List<Line> items, BigDecimal total) {

    public record Line(String product, int quantity, BigDecimal unitPrice) {
    }

    public static OrderDetails from(Order order) {
        String customerName = order.getCustomer().getName();
        List<Line> lines = order.getItems().stream()
                .map(item -> new Line(item.getProduct().getName(), item.getQuantity(), item.getProduct().getPrice()))
                .toList();
        BigDecimal total = lines.stream()
                .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new OrderDetails(order.getId(), order.getStatus(), customerName, lines, total);
    }
}
