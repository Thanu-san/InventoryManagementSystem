package com.stockflow.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Information model representing the return eligibility of a specific purchase order delivery receipt line.
 * Details original receipt quantities, cumulative returns, remaining returnable capacity, and current on-hand stock.
 */
public class ReceiptLineReturnableInfo {

    private final Long receiptItemId;
    private final String receiptNumber;
    private final LocalDateTime receiptDate;
    private final Long purchaseOrderId;
    private final Long productId;
    private final String productSku;
    private final String productName;
    private final int receivedQuantity;
    private final int previouslyReturnedQuantity;
    private final int returnableQuantity;
    private final int currentStockQuantity;
    private final BigDecimal unitCost;

    public ReceiptLineReturnableInfo(Long receiptItemId,
                                     String receiptNumber,
                                     LocalDateTime receiptDate,
                                     Long purchaseOrderId,
                                     Long productId,
                                     String productSku,
                                     String productName,
                                     int receivedQuantity,
                                     int previouslyReturnedQuantity,
                                     int returnableQuantity,
                                     int currentStockQuantity,
                                     BigDecimal unitCost) {
        if (receiptItemId == null || receiptItemId <= 0) {
            throw new IllegalArgumentException("Receipt item ID must be greater than zero.");
        }
        if (receiptNumber == null || receiptNumber.isBlank()) {
            throw new IllegalArgumentException("Receipt number cannot be blank.");
        }
        if (receiptDate == null) {
            throw new IllegalArgumentException("Receipt date cannot be null.");
        }
        if (purchaseOrderId == null || purchaseOrderId <= 0) {
            throw new IllegalArgumentException("Purchase order ID must be greater than zero.");
        }
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("Product ID must be greater than zero.");
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
        if (previouslyReturnedQuantity < 0) {
            throw new IllegalArgumentException("Previously returned quantity cannot be negative.");
        }
        if (returnableQuantity < 0) {
            throw new IllegalArgumentException("Returnable quantity cannot be negative.");
        }
        if (currentStockQuantity < 0) {
            throw new IllegalArgumentException("Current stock quantity cannot be negative.");
        }

        this.receiptItemId = receiptItemId;
        this.receiptNumber = receiptNumber.trim();
        this.receiptDate = receiptDate;
        this.purchaseOrderId = purchaseOrderId;
        this.productId = productId;
        this.productSku = productSku.trim();
        this.productName = productName.trim();
        this.receivedQuantity = receivedQuantity;
        this.previouslyReturnedQuantity = previouslyReturnedQuantity;
        this.returnableQuantity = returnableQuantity;
        this.currentStockQuantity = currentStockQuantity;
        this.unitCost = unitCost != null ? unitCost : BigDecimal.ZERO;
    }

    public Long getReceiptItemId() {
        return receiptItemId;
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }

    public LocalDateTime getReceiptDate() {
        return receiptDate;
    }

    public Long getPurchaseOrderId() {
        return purchaseOrderId;
    }

    public Long getProductId() {
        return productId;
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

    public int getPreviouslyReturnedQuantity() {
        return previouslyReturnedQuantity;
    }

    public int getReturnableQuantity() {
        return returnableQuantity;
    }

    public int getCurrentStockQuantity() {
        return currentStockQuantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public boolean isReturnable() {
        return returnableQuantity > 0 && currentStockQuantity > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReceiptLineReturnableInfo that = (ReceiptLineReturnableInfo) o;
        return receivedQuantity == that.receivedQuantity &&
                previouslyReturnedQuantity == that.previouslyReturnedQuantity &&
                returnableQuantity == that.returnableQuantity &&
                currentStockQuantity == that.currentStockQuantity &&
                Objects.equals(receiptItemId, that.receiptItemId) &&
                Objects.equals(receiptNumber, that.receiptNumber) &&
                Objects.equals(receiptDate, that.receiptDate) &&
                Objects.equals(purchaseOrderId, that.purchaseOrderId) &&
                Objects.equals(productId, that.productId) &&
                Objects.equals(productSku, that.productSku) &&
                Objects.equals(productName, that.productName) &&
                Objects.equals(unitCost, that.unitCost);
    }

    @Override
    public int hashCode() {
        return Objects.hash(receiptItemId, receiptNumber, receiptDate, purchaseOrderId, productId,
                productSku, productName, receivedQuantity, previouslyReturnedQuantity,
                returnableQuantity, currentStockQuantity, unitCost);
    }

    @Override
    public String toString() {
        return "ReceiptLineReturnableInfo{" +
                "receiptItemId=" + receiptItemId +
                ", receiptNumber='" + receiptNumber + '\'' +
                ", receiptDate=" + receiptDate +
                ", purchaseOrderId=" + purchaseOrderId +
                ", productId=" + productId +
                ", productSku='" + productSku + '\'' +
                ", productName='" + productName + '\'' +
                ", receivedQuantity=" + receivedQuantity +
                ", previouslyReturnedQuantity=" + previouslyReturnedQuantity +
                ", returnableQuantity=" + returnableQuantity +
                ", currentStockQuantity=" + currentStockQuantity +
                ", unitCost=" + unitCost +
                '}';
    }
}
