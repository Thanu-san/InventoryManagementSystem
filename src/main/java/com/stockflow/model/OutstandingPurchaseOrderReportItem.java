package com.stockflow.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Immutable report model representing a purchase order that has outstanding (unreceived) goods.
 * Used by the Outstanding Purchase Orders Report.
 */
public class OutstandingPurchaseOrderReportItem {

    private final String orderNumber;
    private final String supplierCode;
    private final String supplierName;
    private final LocalDateTime createdAt;
    private final int distinctLineCount;
    private final int orderedUnits;
    private final int receivedUnits;
    private final int remainingUnits;
    private final PurchaseOrderStatus status;

    public OutstandingPurchaseOrderReportItem(String orderNumber,
                                              String supplierCode,
                                              String supplierName,
                                              LocalDateTime createdAt,
                                              int distinctLineCount,
                                              int orderedUnits,
                                              int receivedUnits,
                                              int remainingUnits,
                                              PurchaseOrderStatus status) {
        if (orderNumber == null || orderNumber.isBlank()) {
            throw new IllegalArgumentException("Order number cannot be blank.");
        }
        if (supplierCode == null || supplierCode.isBlank()) {
            throw new IllegalArgumentException("Supplier code cannot be blank.");
        }
        if (supplierName == null || supplierName.isBlank()) {
            throw new IllegalArgumentException("Supplier name cannot be blank.");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("Creation timestamp cannot be null.");
        }
        if (distinctLineCount < 0) {
            throw new IllegalArgumentException("Distinct line count cannot be negative.");
        }
        if (orderedUnits < 0) {
            throw new IllegalArgumentException("Ordered units cannot be negative.");
        }
        if (receivedUnits < 0) {
            throw new IllegalArgumentException("Received units cannot be negative.");
        }
        if (remainingUnits < 0) {
            throw new IllegalArgumentException("Remaining units cannot be negative.");
        }
        if (status == null) {
            throw new IllegalArgumentException("Purchase order status cannot be null.");
        }

        this.orderNumber = orderNumber.trim();
        this.supplierCode = supplierCode.trim();
        this.supplierName = supplierName.trim();
        this.createdAt = createdAt;
        this.distinctLineCount = distinctLineCount;
        this.orderedUnits = orderedUnits;
        this.receivedUnits = receivedUnits;
        this.remainingUnits = remainingUnits;
        this.status = status;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public String getSupplierCode() {
        return supplierCode;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public int getDistinctLineCount() {
        return distinctLineCount;
    }

    public int getOrderedUnits() {
        return orderedUnits;
    }

    public int getReceivedUnits() {
        return receivedUnits;
    }

    public int getRemainingUnits() {
        return remainingUnits;
    }

    public PurchaseOrderStatus getStatus() {
        return status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OutstandingPurchaseOrderReportItem that = (OutstandingPurchaseOrderReportItem) o;
        return distinctLineCount == that.distinctLineCount &&
                orderedUnits == that.orderedUnits &&
                receivedUnits == that.receivedUnits &&
                remainingUnits == that.remainingUnits &&
                Objects.equals(orderNumber, that.orderNumber) &&
                Objects.equals(supplierCode, that.supplierCode) &&
                Objects.equals(supplierName, that.supplierName) &&
                Objects.equals(createdAt, that.createdAt) &&
                status == that.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderNumber, supplierCode, supplierName, createdAt,
                distinctLineCount, orderedUnits, receivedUnits, remainingUnits, status);
    }

    @Override
    public String toString() {
        return "OutstandingPurchaseOrderReportItem{" +
                "orderNumber='" + orderNumber + '\'' +
                ", supplierCode='" + supplierCode + '\'' +
                ", supplierName='" + supplierName + '\'' +
                ", createdAt=" + createdAt +
                ", distinctLineCount=" + distinctLineCount +
                ", orderedUnits=" + orderedUnits +
                ", receivedUnits=" + receivedUnits +
                ", remainingUnits=" + remainingUnits +
                ", status=" + status +
                '}';
    }
}
