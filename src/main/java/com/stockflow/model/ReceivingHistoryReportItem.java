package com.stockflow.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Immutable report model representing a received goods line item within a specified date range.
 * Used by the Receiving History Report.
 */
public class ReceivingHistoryReportItem {

    private final String receiptNumber;
    private final String orderNumber;
    private final String supplierName;
    private final LocalDateTime receiptDate;
    private final String productSku;
    private final String productName;
    private final int receivedQuantity;
    private final BigDecimal agreedUnitCost;
    private final BigDecimal lineTotalCost;

    public ReceivingHistoryReportItem(String receiptNumber,
                                      String orderNumber,
                                      String supplierName,
                                      LocalDateTime receiptDate,
                                      String productSku,
                                      String productName,
                                      int receivedQuantity,
                                      BigDecimal agreedUnitCost,
                                      BigDecimal lineTotalCost) {
        if (receiptNumber == null || receiptNumber.isBlank()) {
            throw new IllegalArgumentException("Receipt number cannot be blank.");
        }
        if (orderNumber == null || orderNumber.isBlank()) {
            throw new IllegalArgumentException("Order number cannot be blank.");
        }
        if (supplierName == null || supplierName.isBlank()) {
            throw new IllegalArgumentException("Supplier name cannot be blank.");
        }
        if (receiptDate == null) {
            throw new IllegalArgumentException("Receipt date cannot be null.");
        }
        if (productSku == null || productSku.isBlank()) {
            throw new IllegalArgumentException("Product SKU cannot be blank.");
        }
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("Product name cannot be blank.");
        }
        if (receivedQuantity <= 0) {
            throw new IllegalArgumentException("Received quantity must be greater than zero.");
        }
        if (agreedUnitCost == null || agreedUnitCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Agreed unit cost cannot be null or negative.");
        }
        if (lineTotalCost == null || lineTotalCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Line total cost cannot be null or negative.");
        }

        this.receiptNumber = receiptNumber.trim();
        this.orderNumber = orderNumber.trim();
        this.supplierName = supplierName.trim();
        this.receiptDate = receiptDate;
        this.productSku = productSku.trim();
        this.productName = productName.trim();
        this.receivedQuantity = receivedQuantity;
        this.agreedUnitCost = agreedUnitCost;
        this.lineTotalCost = lineTotalCost;
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public LocalDateTime getReceiptDate() {
        return receiptDate;
    }

    public String getProductSku() {
        return productSku;
    }

    public String getProductName() {
        return productName;
    }

    public int getReceivedQuantity() {
        return receivedQuantity;
    }

    public BigDecimal getAgreedUnitCost() {
        return agreedUnitCost;
    }

    public BigDecimal getLineTotalCost() {
        return lineTotalCost;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReceivingHistoryReportItem that = (ReceivingHistoryReportItem) o;
        return receivedQuantity == that.receivedQuantity &&
                Objects.equals(receiptNumber, that.receiptNumber) &&
                Objects.equals(orderNumber, that.orderNumber) &&
                Objects.equals(supplierName, that.supplierName) &&
                Objects.equals(receiptDate, that.receiptDate) &&
                Objects.equals(productSku, that.productSku) &&
                Objects.equals(productName, that.productName) &&
                Objects.equals(agreedUnitCost, that.agreedUnitCost) &&
                Objects.equals(lineTotalCost, that.lineTotalCost);
    }

    @Override
    public int hashCode() {
        return Objects.hash(receiptNumber, orderNumber, supplierName, receiptDate,
                productSku, productName, receivedQuantity, agreedUnitCost, lineTotalCost);
    }

    @Override
    public String toString() {
        return "ReceivingHistoryReportItem{" +
                "receiptNumber='" + receiptNumber + '\'' +
                ", orderNumber='" + orderNumber + '\'' +
                ", supplierName='" + supplierName + '\'' +
                ", receiptDate=" + receiptDate +
                ", productSku='" + productSku + '\'' +
                ", productName='" + productName + '\'' +
                ", receivedQuantity=" + receivedQuantity +
                ", agreedUnitCost=" + agreedUnitCost +
                ", lineTotalCost=" + lineTotalCost +
                '}';
    }
}
