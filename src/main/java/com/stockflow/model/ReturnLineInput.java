package com.stockflow.model;

/**
 * Input DTO representing a single line item return request against a delivery receipt item.
 *
 * @param purchaseOrderReceiptItemId ID of the receipt item being returned
 * @param quantity positive quantity to return
 * @param reason optional reason for the return (e.g., damaged, incorrect item, defective)
 */
public record ReturnLineInput(
        Long purchaseOrderReceiptItemId,
        int quantity,
        String reason
) {
    public ReturnLineInput {
        if (purchaseOrderReceiptItemId == null || purchaseOrderReceiptItemId <= 0) {
            throw new IllegalArgumentException("Purchase order receipt item ID must be greater than zero.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Return quantity must be greater than zero.");
        }
        if (reason != null) {
            reason = reason.trim();
            if (reason.isEmpty()) {
                reason = null;
            }
        }
    }

    public ReturnLineInput(Long purchaseOrderReceiptItemId, int quantity) {
        this(purchaseOrderReceiptItemId, quantity, null);
    }
}
