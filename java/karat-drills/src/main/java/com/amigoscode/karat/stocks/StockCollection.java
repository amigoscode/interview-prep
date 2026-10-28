package com.amigoscode.karat.stocks;

import java.util.ArrayList;
import java.util.List;

/**
 * We are developing stock trading data management software that tracks the prices of different
 * stocks over time and provides useful statistics. A {@link Stock} is data about one stock, a
 * {@link PriceRecord} is the price of a stock on one date, and this class manages the price
 * records of one stock and provides statistics about its prices.
 *
 * <p>Part 1: {@code StockCollectionTest} is not passing because of a bug in this class. Read the
 * code, run the test, fix the bug.
 *
 * <p>Part 2: implement {@link #getBiggestChange}, the largest change in price between two
 * consecutive recorded days.
 */
public class StockCollection {

    public final List<PriceRecord> priceRecords = new ArrayList<>();
    public final Stock stock;

    public StockCollection(Stock stock) {
        this.stock = stock;
    }

    /** Adds a price record to this collection. The record must be for this collection's stock. */
    public void addPriceRecord(PriceRecord priceRecord) {
        if (!priceRecord.stock.equals(this.stock)) {
            throw new IllegalArgumentException(
                    "PriceRecord's Stock is not the same as the StockCollection's");
        }
        priceRecords.add(priceRecord);
    }

    /** Returns the number of price records in this collection. */
    public int getNumPriceRecords() {
        return priceRecords.size();
    }

    /** Returns the maximum price recorded in this collection, or -1 if there are no records. */
    public int getMaxPrice() {
        return priceRecords.stream().mapToInt(record -> record.price).max().getAsInt();
    }

    /** Returns the minimum price recorded in this collection, or -1 if there are no records. */
    public int getMinPrice() {
        return priceRecords.stream().mapToInt(record -> record.price).min().getAsInt();
    }

    /** Returns the average price recorded in this collection, or -1.0 if there are no records. */
    public double getAvgPrice() {
        double total = priceRecords.stream().mapToInt(record -> record.price).sum();
        return total / priceRecords.size();
    }

    /**
     * Part 2: returns the largest change in price between any two consecutive days.
     *
     * <ol>
     *   <li>Sort the price records by date (the "YYYY-MM-DD" format sorts correctly as text);
     *       consecutive means next to each other in that order, whatever the gap in days.
     *   <li>The change of a pair is the later price minus the earlier price, so a drop is
     *       negative. The largest change is the one with the largest absolute value.
     *   <li>On a tie the earliest pair wins.
     *   <li>Return the change with its start date and end date, or {@code null} if fewer than
     *       two records exist.
     * </ol>
     */
    public PriceChange getBiggestChange() {
        throw new UnsupportedOperationException("part 2");
    }
}
