package com.amigoscode.karat.stockledger;

/**
 * An incoming change to one SKU's stock at one warehouse, in the same shape as an inventory
 * record. Updates may arrive out of order; updatedAt says when the warehouse recorded it.
 */
public class InventoryUpdate {
    public final String sku;
    public final int warehouseId;
    public final int onHandQuantity;
    public final int reservedQuantity;
    public final int updatedAt;

    public InventoryUpdate(String sku, int warehouseId, int onHandQuantity, int reservedQuantity, int updatedAt) {
        this.sku = sku;
        this.warehouseId = warehouseId;
        this.onHandQuantity = onHandQuantity;
        this.reservedQuantity = reservedQuantity;
        this.updatedAt = updatedAt;
    }
}
