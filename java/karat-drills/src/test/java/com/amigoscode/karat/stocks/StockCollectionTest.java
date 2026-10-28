package com.amigoscode.karat.stocks;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * This is not a complete test suite, but tests some basic functionality of the code and shows how
 * to use it. One test fails on purpose: that is part 1.
 */
class StockCollectionTest {

    private static StockCollection makeStockCollection(Stock stock, Object[][] priceData) {
        StockCollection stockCollection = new StockCollection(stock);
        for (Object[] priceRecordData : priceData) {
            PriceRecord priceRecord =
                    new PriceRecord(stock, (int) priceRecordData[0], (String) priceRecordData[1]);
            stockCollection.addPriceRecord(priceRecord);
        }
        return stockCollection;
    }

    @Test
    void priceRecord() {
        Stock testStock = new Stock("AAPL", "Apple Inc.");
        PriceRecord testPriceRecord = new PriceRecord(testStock, 100, "2023-07-01");

        assertThat(testPriceRecord.stock).isEqualTo(testStock);
        assertThat(testPriceRecord.price).isEqualTo(100);
        assertThat(testPriceRecord.date).isEqualTo("2023-07-01");
    }

    @Test
    void stockCollection() {
        Stock testStock = new Stock("AAPL", "Apple Inc.");
        StockCollection stockCollection = new StockCollection(testStock);

        assertThat(stockCollection.getNumPriceRecords()).isEqualTo(0);
        assertThat(stockCollection.getMaxPrice()).isEqualTo(-1);
        assertThat(stockCollection.getMinPrice()).isEqualTo(-1);
        assertThat(stockCollection.getAvgPrice()).isCloseTo(-1.0, within(0.001));

        // Price: 110 112 90 105   Date: 2023-06-29 2023-07-01 2023-06-28 2023-07-06
        Object[][] priceData = {
                {110, "2023-06-29"}, {112, "2023-07-01"}, {90, "2023-06-28"}, {105, "2023-07-06"}};
        testStock = new Stock("AAPL", "Apple Inc.");
        stockCollection = makeStockCollection(testStock, priceData);

        assertThat(stockCollection.getNumPriceRecords()).isEqualTo(priceData.length);
        assertThat(stockCollection.getMaxPrice()).isEqualTo(112);
        assertThat(stockCollection.getMinPrice()).isEqualTo(90);
        assertThat(stockCollection.getAvgPrice()).isCloseTo(104.25, within(0.1));
    }

    @Test
    void recordForAnotherStockIsRejected() {
        StockCollection stockCollection = new StockCollection(new Stock("AAPL", "Apple Inc."));
        PriceRecord other = new PriceRecord(new Stock("MSFT", "Microsoft"), 300, "2023-07-01");

        assertThatThrownBy(() -> stockCollection.addPriceRecord(other))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(stockCollection.getNumPriceRecords()).isZero();
    }

    @Test
    void singleRecordIsItsOwnMaxMinAndAverage() {
        Stock testStock = new Stock("AAPL", "Apple Inc.");
        StockCollection stockCollection =
                makeStockCollection(testStock, new Object[][] {{100, "2023-07-01"}});

        assertThat(stockCollection.getMaxPrice()).isEqualTo(100);
        assertThat(stockCollection.getMinPrice()).isEqualTo(100);
        assertThat(stockCollection.getAvgPrice()).isCloseTo(100.0, within(1e-4));
        assertThat(stockCollection.getBiggestChange()).isNull();
    }

    @Test
    void biggestChange() {
        Stock testStock = new Stock("AAPL", "Apple Inc.");
        StockCollection stockCollection = new StockCollection(testStock);

        assertThat(stockCollection.getBiggestChange()).isNull();

        // Price: 110 112 90 105   Date: 2023-06-29 2023-07-01 2023-06-25 2023-07-06
        Object[][] priceData = {
                {110, "2023-06-29"}, {112, "2023-07-01"}, {90, "2023-06-25"}, {105, "2023-07-06"}};
        stockCollection = makeStockCollection(testStock, priceData);

        assertThat(stockCollection.getBiggestChange())
                .isEqualTo(new PriceChange(20, "2023-06-25", "2023-06-29"));

        // Price: 200 210 190 180   Date: 2000-01-04 1999-12-30 2000-01-03 2000-01-01
        Object[][] priceData2 = {
                {200, "2000-01-04"}, {210, "1999-12-30"}, {190, "2000-01-03"}, {180, "2000-01-01"}};
        stockCollection = makeStockCollection(testStock, priceData2);

        assertThat(stockCollection.getBiggestChange())
                .isEqualTo(new PriceChange(-30, "1999-12-30", "2000-01-01"));
    }

    @Test
    void biggestChangeKeepsTheEarliestPairOnATie() {
        Stock testStock = new Stock("AAPL", "Apple Inc.");
        // +10 then -10: same size, the earlier rise wins
        Object[][] priceData = {{100, "2023-07-01"}, {110, "2023-07-02"}, {100, "2023-07-03"}};
        StockCollection stockCollection = makeStockCollection(testStock, priceData);

        assertThat(stockCollection.getBiggestChange())
                .isEqualTo(new PriceChange(10, "2023-07-01", "2023-07-02"));
    }

    @Test
    void biggestChangeWithTwoRecordsIsTheirDifference() {
        Stock testStock = new Stock("AAPL", "Apple Inc.");
        Object[][] priceData = {{120, "2023-07-09"}, {100, "2023-07-02"}};
        StockCollection stockCollection = makeStockCollection(testStock, priceData);

        assertThat(stockCollection.getBiggestChange())
                .isEqualTo(new PriceChange(20, "2023-07-02", "2023-07-09"));
    }
}
