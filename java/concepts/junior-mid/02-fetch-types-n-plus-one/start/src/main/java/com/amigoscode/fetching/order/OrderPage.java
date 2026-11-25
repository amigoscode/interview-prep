package com.amigoscode.fetching.order;

import java.util.List;

/** One page of orders, each with its items. */
public record OrderPage(List<OrderDetails> orders, int page, int size, long totalOrders) {
}
