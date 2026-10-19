package com.stockflow.model;

import java.util.Objects;

/**
 * Represents an aggregated snapshot of inventory metrics.
 * Immutable result type holding summary statistics:
 * - totalProducts: Total number of distinct product records.
 * - totalUnits: Total units across all products in stock.
 * - lowStockProducts: Number of products where quantity <= minimumStock.
 * - outOfStockProducts: Number of products where quantity == 0.
 */
public class InventorySummary {

    private final long totalProducts;
    private final long totalUnits;
    private final long lowStockProducts;
    private final long outOfStockProducts;

    public InventorySummary(long totalProducts, long totalUnits, long lowStockProducts, long outOfStockProducts) {
        if (totalProducts < 0) {
            throw new IllegalArgumentException("Total products cannot be negative.");
        }
        if (totalUnits < 0) {
            throw new IllegalArgumentException("Total units cannot be negative.");
        }
        if (lowStockProducts < 0) {
            throw new IllegalArgumentException("Low-stock products cannot be negative.");
        }
        if (outOfStockProducts < 0) {
            throw new IllegalArgumentException("Out-of-stock products cannot be negative.");
        }

        this.totalProducts = totalProducts;
        this.totalUnits = totalUnits;
        this.lowStockProducts = lowStockProducts;
        this.outOfStockProducts = outOfStockProducts;
    }

    public long getTotalProducts() {
        return totalProducts;
    }

    public long getTotalUnits() {
        return totalUnits;
    }

    public long getLowStockProducts() {
        return lowStockProducts;
    }

    public long getOutOfStockProducts() {
        return outOfStockProducts;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InventorySummary that = (InventorySummary) o;
        return totalProducts == that.totalProducts &&
                totalUnits == that.totalUnits &&
                lowStockProducts == that.lowStockProducts &&
                outOfStockProducts == that.outOfStockProducts;
    }

    @Override
    public int hashCode() {
        return Objects.hash(totalProducts, totalUnits, lowStockProducts, outOfStockProducts);
    }

    @Override
    public String toString() {
        return "InventorySummary{" +
                "totalProducts=" + totalProducts +
                ", totalUnits=" + totalUnits +
                ", lowStockProducts=" + lowStockProducts +
                ", outOfStockProducts=" + outOfStockProducts +
                '}';
    }
}
