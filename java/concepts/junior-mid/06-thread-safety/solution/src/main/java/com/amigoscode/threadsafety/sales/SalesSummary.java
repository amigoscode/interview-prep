package com.amigoscode.threadsafety.sales;

/** Two numbers that only make sense together: revenue is the total of exactly these orders. */
public record SalesSummary(long orders, long revenueCents) {
}
