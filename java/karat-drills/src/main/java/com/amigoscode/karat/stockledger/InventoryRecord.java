package com.amigoscode.karat.stockledger;

/**
 * The current stock state of one SKU at one warehouse. updatedAt is when the warehouse system
 * recorded the stock information, not when Stock Ledger received it.
 */
public class InventoryRecord {
    public final String sku;
    public final int warehouseId;
    public int onHandQuantity;
    public int reservedQuantity;
    public int updatedAt;

    public InventoryRecord(String sku, int warehouseId, int onHandQuantity, int reservedQuantity, int updatedAt) {
        this.sku = sku;
        this.warehouseId = warehouseId;
        this.onHandQuantity = onHandQuantity;
        this.reservedQuantity = reservedQuantity;
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "SKU: " + sku + ", Warehouse: " + warehouseId + ", On hand: " + onHandQuantity
                + ", Reserved: " + reservedQuantity + ", Updated at: " + updatedAt;
    }
}
