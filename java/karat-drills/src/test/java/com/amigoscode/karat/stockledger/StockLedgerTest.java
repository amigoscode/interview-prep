package com.amigoscode.karat.stockledger;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This is not a complete test suite, but tests some basic functionality of the code and shows how
 * to use it. One test fails on purpose: that is part 1.
 */
class StockLedgerTest {

    @Test
    void testApplyInventoryUpdate1() {
        // A newer update's values replace the record's stored values, including a boundary update
        // whose timestamp equals the record's current timestamp.
        StockLedger ledger = new StockLedger();
        ledger.registerInventoryRecord(new InventoryRecord("SKU-1", 101, 100, 20, 3000));

        ledger.applyInventoryUpdate(new InventoryUpdate("SKU-1", 101, 80, 10, 2000));
        InventoryRecord record = ledger.inventoryRecords.get("SKU-1:101");
        assertThat(record.onHandQuantity).isEqualTo(100);
        assertThat(record.reservedQuantity).isEqualTo(20);
        assertThat(record.updatedAt).isEqualTo(3000);
        assertThat(ledger.getAvailableStock("SKU-1", 101)).isEqualTo(80);

        ledger.applyInventoryUpdate(new InventoryUpdate("SKU-1", 101, 120, 30, 4000));
        assertThat(record.onHandQuantity).isEqualTo(120);
        assertThat(record.reservedQuantity).isEqualTo(30);
        assertThat(record.updatedAt).isEqualTo(4000);
        assertThat(ledger.getAvailableStock("SKU-1", 101)).isEqualTo(90);

        // Update timestamp equal to the record's current timestamp (boundary).
        ledger.applyInventoryUpdate(new InventoryUpdate("SKU-1", 101, 200, 50, 4000));
        assertThat(record.onHandQuantity).isEqualTo(200);
        assertThat(record.reservedQuantity).isEqualTo(50);
        assertThat(record.updatedAt).isEqualTo(4000);
    }

    @Test
    void testApplyInventoryUpdate2() {
        // An update for an unknown SKU and warehouse pair is ignored.
        StockLedger ledger = new StockLedger();
        ledger.registerInventoryRecord(new InventoryRecord("SKU-1", 101, 100, 20, 3000));

        ledger.applyInventoryUpdate(new InventoryUpdate("SKU-2", 202, 50, 5, 4000));

        assertThat(ledger.inventoryRecords).hasSize(1);
        assertThat(ledger.inventoryRecords).doesNotContainKey("SKU-2:202");
    }

    @Test
    void testApplyInventoryUpdate3() {
        // Available stock is floored at 0 when an applied update leaves reserved quantity greater
        // than on-hand quantity.
        StockLedger ledger = new StockLedger();
        ledger.registerInventoryRecord(new InventoryRecord("SKU-1", 101, 100, 20, 1000));

        ledger.applyInventoryUpdate(new InventoryUpdate("SKU-1", 101, 30, 50, 2000));

        InventoryRecord record = ledger.inventoryRecords.get("SKU-1:101");
        assertThat(record.onHandQuantity).isEqualTo(30);
        assertThat(record.reservedQuantity).isEqualTo(50);
        assertThat(ledger.getAvailableStock("SKU-1", 101)).isEqualTo(0);
    }

    @Test
    void testPreviewUpdate1() {
        // The impact reflects the on-hand quantity change without modifying the record, in both
        // directions.
        StockLedger ledger = new StockLedger();
        ledger.registerInventoryRecord(new InventoryRecord("SKU-1", 101, 100, 20, 3000));

        Integer impact = ledger.previewUpdate(new InventoryUpdate("SKU-1", 101, 80, 10, 4000));
        assertThat(impact).isEqualTo(-20);

        impact = ledger.previewUpdate(new InventoryUpdate("SKU-1", 101, 150, 30, 1000));
        assertThat(impact).isEqualTo(50);

        InventoryRecord record = ledger.inventoryRecords.get("SKU-1:101");
        assertThat(record.onHandQuantity).isEqualTo(100);
        assertThat(record.updatedAt).isEqualTo(3000);
    }

    @Test
    void testPreviewUpdate2() {
        // A zero-impact update returns 0, and an unknown SKU and warehouse pair returns null.
        StockLedger ledger = new StockLedger();
        ledger.registerInventoryRecord(new InventoryRecord("SKU-1", 101, 100, 20, 1000));

        Integer impact = ledger.previewUpdate(new InventoryUpdate("SKU-1", 101, 100, 30, 2000));
        assertThat(impact).isEqualTo(0);

        impact = ledger.previewUpdate(new InventoryUpdate("SKU-2", 202, 50, 5, 9000));
        assertThat(impact).isNull();
    }

    @Test
    void testGetAvailableStockBySku1() {
        // Available stock is summed across all warehouses for the same SKU, grouped by SKU.
        StockLedger ledger = new StockLedger();
        ledger.registerInventoryRecord(new InventoryRecord("SKU-1", 101, 100, 20, 1000));
        ledger.registerInventoryRecord(new InventoryRecord("SKU-1", 102, 50, 10, 1000));
        ledger.registerInventoryRecord(new InventoryRecord("SKU-2", 101, 30, 5, 1000));

        Map<String, Integer> result = ledger.getAvailableStockBySku();

        assertThat(result).containsOnlyKeys("SKU-1", "SKU-2");
        assertThat(result.get("SKU-1")).isEqualTo(120);
        assertThat(result.get("SKU-2")).isEqualTo(25);
    }

    @Test
    void testGetAvailableStockBySku2() {
        // A record with reserved quantity exceeding on-hand quantity contributes zero, not a
        // negative amount.
        StockLedger ledger = new StockLedger();
        ledger.registerInventoryRecord(new InventoryRecord("SKU-1", 101, 100, 20, 1000));
        ledger.registerInventoryRecord(new InventoryRecord("SKU-1", 102, 10, 50, 1000));

        Map<String, Integer> result = ledger.getAvailableStockBySku();

        assertThat(result).containsOnlyKeys("SKU-1");
        assertThat(result.get("SKU-1")).isEqualTo(80);
    }

    @Test
    void testGetAvailableStockBySku3() {
        // An empty ledger returns an empty result, and a SKU whose only record has zero available
        // stock still appears with a value of 0.
        StockLedger ledger = new StockLedger();
        assertThat(ledger.getAvailableStockBySku()).isEmpty();

        ledger.registerInventoryRecord(new InventoryRecord("SKU-1", 101, 10, 50, 1000));

        Map<String, Integer> result = ledger.getAvailableStockBySku();
        assertThat(result).containsOnlyKeys("SKU-1");
        assertThat(result.get("SKU-1")).isEqualTo(0);
    }

    @Test
    void availableStockForUnknownPairIsZero() {
        StockLedger ledger = new StockLedger();
        ledger.registerInventoryRecord(new InventoryRecord("SKU-1", 101, 100, 20, 1000));

        assertThat(ledger.getAvailableStock("SKU-1", 999)).isZero();
        assertThat(ledger.getAvailableStock("SKU-9", 101)).isZero();
    }

    @Test
    void reRegisteringAPairReplacesTheRecord() {
        StockLedger ledger = new StockLedger();
        ledger.registerInventoryRecord(new InventoryRecord("SKU-1", 101, 100, 20, 1000));
        ledger.registerInventoryRecord(new InventoryRecord("SKU-1", 101, 5, 0, 500));

        assertThat(ledger.inventoryRecords).hasSize(1);
        assertThat(ledger.getAvailableStock("SKU-1", 101)).isEqualTo(5);
    }

    @Test
    void availableStockBySkuReflectsAppliedUpdates() {
        StockLedger ledger = new StockLedger();
        ledger.registerInventoryRecord(new InventoryRecord("SKU-1", 101, 100, 20, 1000));
        ledger.registerInventoryRecord(new InventoryRecord("SKU-1", 102, 50, 10, 1000));

        ledger.applyInventoryUpdate(new InventoryUpdate("SKU-1", 101, 10, 0, 2000)); // applied
        ledger.applyInventoryUpdate(new InventoryUpdate("SKU-1", 102, 0, 0, 500));   // stale, ignored

        assertThat(ledger.getAvailableStockBySku().get("SKU-1")).isEqualTo(50);
    }
}
