package com.amigoscode.karat.stocks;

/**
 * The result of {@link StockCollection#getBiggestChange()}: the change in price (end price minus
 * start price, so a drop is negative) between two consecutive recorded days, and the two dates.
 */
public record PriceChange(int change, String startDate, String endDate) {
}
