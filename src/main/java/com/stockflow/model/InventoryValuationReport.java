package com.stockflow.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents the comprehensive inventory valuation report.
 * Immutable result type holding valuation items for all catalog products
 * and aggregate summary totals (total quantity and total monetary value).
 */
public class InventoryValuationReport {

    private final List<InventoryValuationItem> items;
    private final long totalQuantity;
    private final BigDecimal totalValue;

    public InventoryValuationReport(List<InventoryValuationItem> items, long totalQuantity, BigDecimal totalValue) {
        if (items == null) {
            throw new IllegalArgumentException("Items list cannot be null.");
        }
        if (totalQuantity < 0) {
            throw new IllegalArgumentException("Total quantity cannot be negative.");
        }
        if (totalValue == null || totalValue.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Total value cannot be null or negative.");
        }

        this.items = List.copyOf(items);
        this.totalQuantity = totalQuantity;
        this.totalValue = totalValue;
    }

    /**
     * Creates an empty report with 0 units and 0.00 total valuation.
     */
    public static InventoryValuationReport empty() {
        return new InventoryValuationReport(Collections.emptyList(), 0L, new BigDecimal("0.00"));
    }

    public List<InventoryValuationItem> getItems() {
        return items;
    }

    public long getTotalQuantity() {
        return totalQuantity;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InventoryValuationReport that = (InventoryValuationReport) o;
        return totalQuantity == that.totalQuantity &&
                Objects.equals(items, that.items) &&
                Objects.equals(totalValue, that.totalValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(items, totalQuantity, totalValue);
    }

    @Override
    public String toString() {
        return "InventoryValuationReport{" +
                "items=" + items +
                ", totalQuantity=" + totalQuantity +
                ", totalValue=" + totalValue +
                '}';
    }
}
