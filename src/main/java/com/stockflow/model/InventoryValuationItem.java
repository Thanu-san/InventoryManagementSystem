package com.stockflow.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Represents the inventory valuation for a single product.
 * Immutable result model holding product identification, price, quantity, and computed value:
 * - sku: Unique product stock keeping unit.
 * - name: Product name.
 * - unitPrice: Product price per unit.
 * - quantity: Current stock quantity on hand.
 * - inventoryValue: Computed valuation (unitPrice × quantity).
 */
public class InventoryValuationItem {

    private final String sku;
    private final String name;
    private final BigDecimal unitPrice;
    private final int quantity;
    private final BigDecimal inventoryValue;

    public InventoryValuationItem(String sku, String name, BigDecimal unitPrice, int quantity, BigDecimal inventoryValue) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("Product SKU cannot be blank.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Product name cannot be blank.");
        }
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Unit price cannot be null or negative.");
        }
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative.");
        }
        if (inventoryValue == null || inventoryValue.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Inventory value cannot be null or negative.");
        }

        this.sku = sku.trim();
        this.name = name.trim();
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.inventoryValue = inventoryValue;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getInventoryValue() {
        return inventoryValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InventoryValuationItem that = (InventoryValuationItem) o;
        return quantity == that.quantity &&
                Objects.equals(sku, that.sku) &&
                Objects.equals(name, that.name) &&
                Objects.equals(unitPrice, that.unitPrice) &&
                Objects.equals(inventoryValue, that.inventoryValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sku, name, unitPrice, quantity, inventoryValue);
    }

    @Override
    public String toString() {
        return "InventoryValuationItem{" +
                "sku='" + sku + '\'' +
                ", name='" + name + '\'' +
                ", unitPrice=" + unitPrice +
                ", quantity=" + quantity +
                ", inventoryValue=" + inventoryValue +
                '}';
    }
}
