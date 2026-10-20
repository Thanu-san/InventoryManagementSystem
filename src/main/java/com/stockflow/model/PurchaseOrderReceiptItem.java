package com.stockflow.model;

import java.util.Objects;

/**
 * Represents a single received product item within a purchase order delivery receipt.
 * Directly links the receipt to the specific purchase order line, the product,
 * and the exact STOCK_IN inventory transaction generated.
 */
public class PurchaseOrderReceiptItem {

    private Long id;
    private Long receiptId;
    private final Long purchaseOrderItemId;
    private final Long productId;
    private final int quantity;
    private Long inventoryTransactionId;

    /**
     * Constructor before persistence.
     */
    public PurchaseOrderReceiptItem(Long purchaseOrderItemId, Long productId, int quantity) {
        this(null, null, purchaseOrderItemId, productId, quantity, null);
    }

    /**
     * Constructor when receipt ID and inventory transaction ID are assigned.
     */
    public PurchaseOrderReceiptItem(Long receiptId, Long purchaseOrderItemId, Long productId,
                                   int quantity, Long inventoryTransactionId) {
        this(null, receiptId, purchaseOrderItemId, productId, quantity, inventoryTransactionId);
    }

    /**
     * Full reconstitution constructor from database.
     */
    public PurchaseOrderReceiptItem(Long id, Long receiptId, Long purchaseOrderItemId,
                                   Long productId, int quantity, Long inventoryTransactionId) {
        if (purchaseOrderItemId == null || purchaseOrderItemId <= 0) {
            throw new IllegalArgumentException("Purchase order item ID must be greater than zero.");
        }
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("Product ID must be greater than zero.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Received quantity must be greater than zero.");
        }

        if (id != null) {
            setId(id);
        }
        this.receiptId = receiptId;
        this.purchaseOrderItemId = purchaseOrderItemId;
        this.productId = productId;
        this.quantity = quantity;
        this.inventoryTransactionId = inventoryTransactionId;
    }

    // --- ID: Can only be assigned once upon persistence ---
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        if (this.id != null) {
            throw new IllegalStateException("Receipt item ID is already assigned and cannot be changed.");
        }
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Receipt item ID must be greater than zero.");
        }
        this.id = id;
    }

    public Long getReceiptId() {
        return receiptId;
    }

    public void setReceiptId(Long receiptId) {
        if (this.receiptId != null && !this.receiptId.equals(receiptId)) {
            throw new IllegalStateException("Receipt ID is already assigned and cannot be changed.");
        }
        if (receiptId == null || receiptId <= 0) {
            throw new IllegalArgumentException("Receipt ID must be greater than zero.");
        }
        this.receiptId = receiptId;
    }

    public Long getPurchaseOrderItemId() {
        return purchaseOrderItemId;
    }

    public Long getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
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
        if (!(o instanceof PurchaseOrderReceiptItem that)) return false;
        if (id != null && that.id != null) return Objects.equals(id, that.id);
        return Objects.equals(receiptId, that.receiptId) &&
               Objects.equals(purchaseOrderItemId, that.purchaseOrderItemId);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : Objects.hash(receiptId, purchaseOrderItemId);
    }

    @Override
    public String toString() {
        return "PurchaseOrderReceiptItem{" +
                "id=" + id +
                ", receiptId=" + receiptId +
                ", purchaseOrderItemId=" + purchaseOrderItemId +
                ", productId=" + productId +
                ", quantity=" + quantity +
                ", inventoryTransactionId=" + inventoryTransactionId +
                '}';
    }
}
