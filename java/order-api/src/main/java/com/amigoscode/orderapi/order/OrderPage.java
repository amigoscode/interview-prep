package com.amigoscode.orderapi.order;

import java.util.List;

/** One page of a customer's orders, newest first. */
public record OrderPage(List<Order> orders, int page, int size, long totalElements, int totalPages) {
}
