package com.stockflow.model;

import java.time.LocalDateTime;

/**
 * Represents an immutable historical inventory stock movement.
 * Encapsulates transaction state and protects its internal integrity:
 * - ID can only be assigned once upon persistence.
 * - Product ID, transaction type, quantity, and timestamp are immutable once recorded.
 */
public class InventoryTransaction {

    private Long id;
    private final Long productId;
    private final TransactionType type;
    private final int quantity;
    private final LocalDateTime createdAt;

    // Constructor without ID (used when creating a new transaction before persistence)
    public InventoryTransaction(Long productId, TransactionType type, int quantity) {
        this(null, productId, type, quantity, LocalDateTime.now());
    }

    // Full constructor (used when reconstituting an existing transaction from storage)
    public InventoryTransaction(Long id, Long productId, TransactionType type, int quantity, LocalDateTime createdAt) {
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("Product ID must be greater than zero.");
        }
        if (type == null) {
            throw new IllegalArgumentException("Transaction type cannot be null.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Transaction quantity must be greater than zero.");
        }

        if (id != null) {
            setId(id);
        }
        this.productId = productId;
        this.type = type;
        this.quantity = quantity;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    // --- ID: Can only be assigned once upon persistence ---
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        if (this.id != null) {
            throw new IllegalStateException("Transaction ID is already assigned and cannot be changed.");
        }
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Transaction ID must be greater than zero.");
        }
        this.id = id;
    }

    public Long getProductId() {
        return productId;
    }

    public TransactionType getType() {
        return type;
    }

    public int getQuantity() {
        return quantity;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "InventoryTransaction{" +
                "id=" + id +
                ", productId=" + productId +
                ", type=" + type +
                ", quantity=" + quantity +
                ", createdAt=" + createdAt +
                '}';
    }
}
