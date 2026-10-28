package com.amigoscode.karat.stocks;

/** A single price record of a stock: the stock, the price and the date in "YYYY-MM-DD" format. */
public class PriceRecord {
    public final Stock stock;
    public final int price;
    public final String date;

    public PriceRecord(Stock stock, int price, String date) {
        this.stock = stock;
        this.price = price;
        this.date = date;
    }
}
