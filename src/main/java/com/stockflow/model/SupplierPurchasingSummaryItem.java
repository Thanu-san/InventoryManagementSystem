package com.stockflow.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable report model summarizing committed purchasing metrics and received delivery value per supplier.
 * Used by the Supplier Purchasing Summary Report.
 *
 * <p>Status and calculation rules:
 * - Order count reflects committed purchase orders (status PLACED, PARTIALLY_RECEIVED, RECEIVED).
 * - DRAFT and CANCELLED orders are excluded from active commitments.
 * - Total purchase-order value represents recorded commitments at order placement, not actual payments made.
 * - Received value is computed directly from physical delivery quantities and historical agreed unit costs.
 */
public class SupplierPurchasingSummaryItem {

    private final String supplierCode;
    private final String supplierName;
    private final int orderCount;
    private final int totalOrderedUnits;
    private final int totalReceivedUnits;
    private final int totalOutstandingUnits;
    private final BigDecimal totalOrderValue;
    private final BigDecimal totalReceivedValue;

    public SupplierPurchasingSummaryItem(String supplierCode,
                                         String supplierName,
                                         int orderCount,
                                         int totalOrderedUnits,
                                         int totalReceivedUnits,
                                         int totalOutstandingUnits,
                                         BigDecimal totalOrderValue,
                                         BigDecimal totalReceivedValue) {
        if (supplierCode == null || supplierCode.isBlank()) {
            throw new IllegalArgumentException("Supplier code cannot be blank.");
        }
        if (supplierName == null || supplierName.isBlank()) {
            throw new IllegalArgumentException("Supplier name cannot be blank.");
        }
        if (orderCount < 0) {
            throw new IllegalArgumentException("Order count cannot be negative.");
        }
        if (totalOrderedUnits < 0) {
            throw new IllegalArgumentException("Total ordered units cannot be negative.");
        }
        if (totalReceivedUnits < 0) {
            throw new IllegalArgumentException("Total received units cannot be negative.");
        }
        if (totalOutstandingUnits < 0) {
            throw new IllegalArgumentException("Total outstanding units cannot be negative.");
        }
        if (totalOrderValue == null || totalOrderValue.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Total order value cannot be null or negative.");
        }
        if (totalReceivedValue == null || totalReceivedValue.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Total received value cannot be null or negative.");
        }

        this.supplierCode = supplierCode.trim();
        this.supplierName = supplierName.trim();
        this.orderCount = orderCount;
        this.totalOrderedUnits = totalOrderedUnits;
        this.totalReceivedUnits = totalReceivedUnits;
        this.totalOutstandingUnits = totalOutstandingUnits;
        this.totalOrderValue = totalOrderValue;
        this.totalReceivedValue = totalReceivedValue;
    }

    public String getSupplierCode() {
        return supplierCode;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public int getOrderCount() {
        return orderCount;
    }

    public int getTotalOrderedUnits() {
        return totalOrderedUnits;
    }

    public int getTotalReceivedUnits() {
        return totalReceivedUnits;
    }

    public int getTotalOutstandingUnits() {
        return totalOutstandingUnits;
    }

    public BigDecimal getTotalOrderValue() {
        return totalOrderValue;
    }

    public BigDecimal getTotalReceivedValue() {
        return totalReceivedValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SupplierPurchasingSummaryItem that = (SupplierPurchasingSummaryItem) o;
        return orderCount == that.orderCount &&
                totalOrderedUnits == that.totalOrderedUnits &&
                totalReceivedUnits == that.totalReceivedUnits &&
                totalOutstandingUnits == that.totalOutstandingUnits &&
                Objects.equals(supplierCode, that.supplierCode) &&
                Objects.equals(supplierName, that.supplierName) &&
                Objects.equals(totalOrderValue, that.totalOrderValue) &&
                Objects.equals(totalReceivedValue, that.totalReceivedValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(supplierCode, supplierName, orderCount,
                totalOrderedUnits, totalReceivedUnits, totalOutstandingUnits,
                totalOrderValue, totalReceivedValue);
    }

    @Override
    public String toString() {
        return "SupplierPurchasingSummaryItem{" +
                "supplierCode='" + supplierCode + '\'' +
                ", supplierName='" + supplierName + '\'' +
                ", orderCount=" + orderCount +
                ", totalOrderedUnits=" + totalOrderedUnits +
                ", totalReceivedUnits=" + totalReceivedUnits +
                ", totalOutstandingUnits=" + totalOutstandingUnits +
                ", totalOrderValue=" + totalOrderValue +
                ", totalReceivedValue=" + totalReceivedValue +
                '}';
    }
}
