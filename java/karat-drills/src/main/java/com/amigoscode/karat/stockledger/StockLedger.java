package com.amigoscode.karat.stockledger;

import java.util.HashMap;
import java.util.Map;

/**
 * We are building Stock Ledger, a back-end system that tracks inventory across warehouses for an
 * e-commerce fulfillment platform. Inventory is tracked per SKU at each warehouse. A record's
 * available stock is onHandQuantity minus reservedQuantity, floored at 0. Updates come from
 * multiple warehouse systems and may arrive out of order: an update whose updatedAt is older than
 * the record's current updatedAt contains outdated information and must not replace the record.
 *
 * <p>Part 1: {@code StockLedgerTest} is not passing because of a bug in this class. Read the
 * code, run the tests, fix the bug.
 *
 * <p>Part 2: we are letting operations teams preview an update's impact and monitor stock per
 * SKU. Implement {@link #previewUpdate} and {@link #getAvailableStockBySku}.
 */
public class StockLedger {

    public final Map<String, InventoryRecord> inventoryRecords = new HashMap<>();

    public String recordKey(String sku, int warehouseId) {
        return sku + ":" + warehouseId;
    }

    public void registerInventoryRecord(InventoryRecord record) {
        inventoryRecords.put(recordKey(record.sku, record.warehouseId), record);
    }

    /**
     * Applies an update to the record for the same SKU and warehouse. An update for an unknown
     * SKU and warehouse pair is ignored. An update older than the record's current updatedAt is
     * outdated and is ignored; an update with the same updatedAt is applied.
     */
    public void applyInventoryUpdate(InventoryUpdate update) {
        InventoryRecord record = findRecord(update);
        if (record == null) {
            return;
        }
        // Bug was never comparing updatedAt, so a stale out-of-order update overwrote the record.
        if (update.updatedAt < record.updatedAt) {
            return;
        }
        record.onHandQuantity = update.onHandQuantity;
        record.reservedQuantity = update.reservedQuantity;
        record.updatedAt = update.updatedAt;
    }

    /** Available stock for one SKU at one warehouse, floored at 0; 0 for an unknown pair. */
    public int getAvailableStock(String sku, int warehouseId) {
        InventoryRecord record = inventoryRecords.get(recordKey(sku, warehouseId));
        return record == null ? 0 : availableStock(record);
    }

    /**
     * Part 2.1: returns the change in on-hand quantity the update would cause, without applying
     * it: the update's on-hand quantity minus the record's current on-hand quantity. If the
     * update refers to an unknown SKU and warehouse pair, returns null instead of a number.
     */
    public Integer previewUpdate(InventoryUpdate update) {
        InventoryRecord record = findRecord(update);
        if (record == null) {
            return null;
        }
        return update.onHandQuantity - record.onHandQuantity;
    }

    /**
     * Part 2.2: returns a map from each SKU to its total available stock summed across all
     * warehouses. A SKU whose records have no available stock still appears, with 0.
     */
    public Map<String, Integer> getAvailableStockBySku() {
        Map<String, Integer> totals = new HashMap<>();
        for (InventoryRecord record : inventoryRecords.values()) {
            totals.merge(record.sku, availableStock(record), Integer::sum);
        }
        return totals;
    }

    private InventoryRecord findRecord(InventoryUpdate update) {
        return inventoryRecords.get(recordKey(update.sku, update.warehouseId));
    }

    private static int availableStock(InventoryRecord record) {
        return Math.max(record.onHandQuantity - record.reservedQuantity, 0);
    }
}
