package com.amigoscode.karat.stocks;

import java.util.Objects;

/** Data about a particular stock. */
public class Stock {
    public final String symbol;
    public final String name;

    public Stock(String symbol, String name) {
        this.symbol = symbol;
        this.name = name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        Stock stock = (Stock) other;
        return symbol.equals(stock.symbol) && name.equals(stock.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, name);
    }

    @Override
    public String toString() {
        return "Stock " + symbol + " (" + name + ")";
    }
}
