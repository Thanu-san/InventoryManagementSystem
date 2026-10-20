package com.stockflow.model;

import java.util.Objects;

/**
 * Represents a single returned line item within a supplier return.
 * Directly links the return line to the specific delivery receipt line,
 * the product, and the exact STOCK_OUT inventory audit transaction generated.
 */
public class SupplierReturnItem {

    private Long id;
    private Long supplierReturnId;
    private final Long purchaseOrderReceiptItemId;
    private final Long productId;
    private final int quantity;
    private final String reason;
    private Long inventoryTransactionId;

    /**
     * Constructor before persistence.
     */
    public SupplierReturnItem(Long purchaseOrderReceiptItemId, Long productId, int quantity, String reason) {
        this(null, null, purchaseOrderReceiptItemId, productId, quantity, reason, null);
    }

    /**
     * Constructor when supplier return ID and inventory transaction ID are assigned.
     */
    public SupplierReturnItem(Long supplierReturnId, Long purchaseOrderReceiptItemId, Long productId,
                              int quantity, String reason, Long inventoryTransactionId) {
        this(null, supplierReturnId, purchaseOrderReceiptItemId, productId, quantity, reason, inventoryTransactionId);
    }

    /**
     * Full reconstitution constructor from database.
     */
    public SupplierReturnItem(Long id, Long supplierReturnId, Long purchaseOrderReceiptItemId,
                              Long productId, int quantity, String reason, Long inventoryTransactionId) {
        if (purchaseOrderReceiptItemId == null || purchaseOrderReceiptItemId <= 0) {
            throw new IllegalArgumentException("Purchase order receipt item ID must be greater than zero.");
        }
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("Product ID must be greater than zero.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Returned quantity must be greater than zero.");
        }

        if (id != null) {
            setId(id);
        }
        this.supplierReturnId = supplierReturnId;
        this.purchaseOrderReceiptItemId = purchaseOrderReceiptItemId;
        this.productId = productId;
        this.quantity = quantity;
        this.reason = (reason == null || reason.isBlank()) ? null : reason.trim();
        this.inventoryTransactionId = inventoryTransactionId;
    }

    // --- ID: Can only be assigned once upon persistence ---
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        if (this.id != null) {
            throw new IllegalStateException("Supplier return item ID is already assigned and cannot be changed.");
        }
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Supplier return item ID must be greater than zero.");
        }
        this.id = id;
    }

    public Long getSupplierReturnId() {
        return supplierReturnId;
    }

    public void setSupplierReturnId(Long supplierReturnId) {
        if (this.supplierReturnId != null && !this.supplierReturnId.equals(supplierReturnId)) {
            throw new IllegalStateException("Supplier return ID is already assigned and cannot be changed.");
        }
        if (supplierReturnId == null || supplierReturnId <= 0) {
            throw new IllegalArgumentException("Supplier return ID must be greater than zero.");
        }
        this.supplierReturnId = supplierReturnId;
    }

    public Long getPurchaseOrderReceiptItemId() {
        return purchaseOrderReceiptItemId;
    }

    public Long getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getReason() {
        return reason;
    }

    public Long getInventoryTransactionId() {
        return inventoryTransactionId;
    }

    public void setInventoryTransactionId(Long inventoryTransactionId) {
        if (this.inventoryTransactionId != null && !this.inventoryTransactionId.equals(inventoryTransactionId)) {
            throw new IllegalStateException("Inventory transaction ID is already assigned and cannot be changed.");
        }
        if (inventoryTransactionId == null || inventoryTransactionId <= 0) {
            throw new IllegalArgumentException("Inventory transaction ID must be greater than zero.");
        }
        this.inventoryTransactionId = inventoryTransactionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SupplierReturnItem that = (SupplierReturnItem) o;
        if (id != null && that.id != null) {
            return Objects.equals(id, that.id);
        }
        return quantity == that.quantity &&
                Objects.equals(supplierReturnId, that.supplierReturnId) &&
                Objects.equals(purchaseOrderReceiptItemId, that.purchaseOrderReceiptItemId) &&
                Objects.equals(productId, that.productId) &&
                Objects.equals(reason, that.reason) &&
                Objects.equals(inventoryTransactionId, that.inventoryTransactionId);
    }

    @Override
    public int hashCode() {
        if (id != null) {
            return Objects.hash(id);
        }
        return Objects.hash(supplierReturnId, purchaseOrderReceiptItemId, productId, quantity, reason, inventoryTransactionId);
    }

    @Override
    public String toString() {
        return "SupplierReturnItem{" +
                "id=" + id +
                ", supplierReturnId=" + supplierReturnId +
                ", purchaseOrderReceiptItemId=" + purchaseOrderReceiptItemId +
                ", productId=" + productId +
                ", quantity=" + quantity +
                ", reason='" + reason + '\'' +
                ", inventoryTransactionId=" + inventoryTransactionId +
                '}';
    }
}
