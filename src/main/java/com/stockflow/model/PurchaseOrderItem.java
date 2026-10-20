package com.stockflow.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Represents a single line item within a purchase order.
 * Snapshots the product's SKU and name at order time to preserve historical integrity,
 * and maintains the agreed unit purchase cost, ordered quantity, and cumulative received quantity.
 */
public class PurchaseOrderItem {

    private Long id;
    private Long purchaseOrderId;
    private final Long productId;
    private final String productSku;
    private final String productName;
    private int orderedQuantity;
    private BigDecimal unitCost;
    private int receivedQuantity = 0;

    /**
     * Constructor for creating a new line item before persistence.
     */
    public PurchaseOrderItem(Long productId, String productSku, String productName,
                             int orderedQuantity, BigDecimal unitCost) {
        this(null, null, productId, productSku, productName, orderedQuantity, unitCost, 0);
    }

    /**
     * Constructor when purchase order ID is known before item ID persistence.
     */
    public PurchaseOrderItem(Long purchaseOrderId, Long productId, String productSku,
                             String productName, int orderedQuantity, BigDecimal unitCost) {
        this(null, purchaseOrderId, productId, productSku, productName, orderedQuantity, unitCost, 0);
    }

    /**
     * Reconstitution constructor without explicit receivedQuantity (defaults to 0).
     */
    public PurchaseOrderItem(Long id, Long purchaseOrderId, Long productId, String productSku,
                             String productName, int orderedQuantity, BigDecimal unitCost) {
        this(id, purchaseOrderId, productId, productSku, productName, orderedQuantity, unitCost, 0);
    }

    /**
     * Full constructor for reconstituting an existing line item from storage with receipt tracking.
     */
    public PurchaseOrderItem(Long id, Long purchaseOrderId, Long productId, String productSku,
                             String productName, int orderedQuantity, BigDecimal unitCost,
                             int receivedQuantity) {
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("Product ID must be greater than zero.");
        }
        if (productSku == null || productSku.isBlank()) {
            throw new IllegalArgumentException("Product SKU cannot be blank.");
        }
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("Product name cannot be blank.");
        }

        if (id != null) {
            setId(id);
        }
        this.purchaseOrderId = purchaseOrderId;
        this.productId = productId;
        this.productSku = productSku.trim();
        this.productName = productName.trim();
        setOrderedQuantity(orderedQuantity);
        setUnitCost(unitCost);
        setReceivedQuantity(receivedQuantity);
    }

    // --- ID: Can only be assigned once upon persistence ---
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        if (this.id != null) {
            throw new IllegalStateException("Line item ID is already assigned and cannot be changed.");
        }
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Line item ID must be greater than zero.");
        }
        this.id = id;
    }

    public Long getPurchaseOrderId() {
        return purchaseOrderId;
    }

    public void setPurchaseOrderId(Long purchaseOrderId) {
        if (this.purchaseOrderId != null && !this.purchaseOrderId.equals(purchaseOrderId)) {
            throw new IllegalStateException("Purchase order ID is already assigned and cannot be changed.");
        }
        if (purchaseOrderId == null || purchaseOrderId <= 0) {
            throw new IllegalArgumentException("Purchase order ID must be greater than zero.");
        }
        this.purchaseOrderId = purchaseOrderId;
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

    public int getOrderedQuantity() {
        return orderedQuantity;
    }

    public void setOrderedQuantity(int orderedQuantity) {
        if (orderedQuantity <= 0) {
            throw new IllegalArgumentException("Ordered quantity must be greater than zero.");
        }
        this.orderedQuantity = orderedQuantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(BigDecimal unitCost) {
        if (unitCost == null || unitCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Unit cost must be greater than or equal to zero.");
        }
        this.unitCost = unitCost;
    }

    public int getReceivedQuantity() {
        return receivedQuantity;
    }

    public void setReceivedQuantity(int receivedQuantity) {
        if (receivedQuantity < 0) {
            throw new IllegalArgumentException("Received quantity cannot be negative.");
        }
        this.receivedQuantity = receivedQuantity;
    }

    public void addReceivedQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity to add must be greater than zero.");
        }
        this.receivedQuantity += quantity;
    }

    /**
     * Calculates the remaining quantity to be received: orderedQuantity - receivedQuantity.
     */
    public int getRemainingQuantity() {
        return Math.max(0, orderedQuantity - receivedQuantity);
    }

    /**
     * Checks if this line item has been fully fulfilled.
     */
    public boolean isFullyReceived() {
        return receivedQuantity >= orderedQuantity;
    }

    /**
     * Calculates the line item total: unitCost * orderedQuantity.
     */
    public BigDecimal getLineTotal() {
        return unitCost.multiply(BigDecimal.valueOf(orderedQuantity));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PurchaseOrderItem that)) return false;
        if (id != null && that.id != null) return Objects.equals(id, that.id);
        return Objects.equals(purchaseOrderId, that.purchaseOrderId) &&
               Objects.equals(productId, that.productId);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : Objects.hash(purchaseOrderId, productId);
    }

    @Override
    public String toString() {
        return "PurchaseOrderItem{" +
                "id=" + id +
                ", purchaseOrderId=" + purchaseOrderId +
                ", productId=" + productId +
                ", productSku='" + productSku + '\'' +
                ", productName='" + productName + '\'' +
                ", orderedQuantity=" + orderedQuantity +
                ", receivedQuantity=" + receivedQuantity +
                ", remainingQuantity=" + getRemainingQuantity() +
                ", unitCost=" + unitCost +
                ", lineTotal=" + getLineTotal() +
                '}';
    }
}
