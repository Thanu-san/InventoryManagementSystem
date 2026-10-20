package com.stockflow.model;

/**
 * Value object representing a requested receipt quantity for a specific purchase order line item.
 */
public record ReceiptLineInput(Long purchaseOrderItemId, int quantity) {

    public ReceiptLineInput {
        if (purchaseOrderItemId == null || purchaseOrderItemId <= 0) {
            throw new IllegalArgumentException("Purchase order item ID must be greater than zero.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Receipt quantity must be greater than zero.");
        }
    }
}
