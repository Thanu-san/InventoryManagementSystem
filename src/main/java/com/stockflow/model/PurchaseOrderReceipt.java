package com.stockflow.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents an audit receipt documenting a physical delivery of goods against a purchase order.
 * Maintains header details (receipt number, timestamp, optional notes) and line item records.
 */
public class PurchaseOrderReceipt {

    private Long id;
    private final String receiptNumber;
    private final Long purchaseOrderId;
    private final LocalDateTime receiptDate;
    private final String notes;
    private final List<PurchaseOrderReceiptItem> items = new ArrayList<>();

    /**
     * Constructor before persistence.
     */
    public PurchaseOrderReceipt(String receiptNumber, Long purchaseOrderId, String notes) {
        this(null, receiptNumber, purchaseOrderId, LocalDateTime.now(), notes, null);
    }

    /**
     * Full reconstitution constructor from database.
     */
    public PurchaseOrderReceipt(Long id, String receiptNumber, Long purchaseOrderId,
                                LocalDateTime receiptDate, String notes,
                                List<PurchaseOrderReceiptItem> items) {
        if (receiptNumber == null || receiptNumber.isBlank()) {
            throw new IllegalArgumentException("Receipt number cannot be blank.");
        }
        if (purchaseOrderId == null || purchaseOrderId <= 0) {
            throw new IllegalArgumentException("Purchase order ID must be greater than zero.");
        }

        if (id != null) {
            setId(id);
        }
        this.receiptNumber = receiptNumber.trim();
        this.purchaseOrderId = purchaseOrderId;
        this.receiptDate = receiptDate != null ? receiptDate : LocalDateTime.now();
        this.notes = (notes == null || notes.isBlank()) ? null : notes.trim();

        if (items != null) {
            for (PurchaseOrderReceiptItem item : items) {
                if (item != null) {
                    this.items.add(item);
                }
            }
        }
    }

    // --- ID: Can only be assigned once upon persistence ---
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        if (this.id != null) {
            throw new IllegalStateException("Receipt ID is already assigned and cannot be changed.");
        }
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Receipt ID must be greater than zero.");
        }
        this.id = id;
    }

    public String getReceiptNumber() {
        return receiptNumber;
    }

    public Long getPurchaseOrderId() {
        return purchaseOrderId;
    }

    public LocalDateTime getReceiptDate() {
        return receiptDate;
    }

    public String getNotes() {
        return notes;
    }

    public List<PurchaseOrderReceiptItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public void addItem(PurchaseOrderReceiptItem item) {
        if (item == null) {
            throw new IllegalArgumentException("Receipt item cannot be null.");
        }
        if (this.id != null && item.getReceiptId() == null) {
            item.setReceiptId(this.id);
        }
        this.items.add(item);
    }

    /**
     * Calculates total quantity of goods received across all lines in this delivery receipt.
     */
    public int getTotalReceivedQuantity() {
        int total = 0;
        for (PurchaseOrderReceiptItem item : items) {
            total += item.getQuantity();
        }
        return total;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PurchaseOrderReceipt that)) return false;
        if (id != null && that.id != null) return Objects.equals(id, that.id);
        return Objects.equals(receiptNumber, that.receiptNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(receiptNumber);
    }

    @Override
    public String toString() {
        return "PurchaseOrderReceipt{" +
                "id=" + id +
                ", receiptNumber='" + receiptNumber + '\'' +
                ", purchaseOrderId=" + purchaseOrderId +
                ", receiptDate=" + receiptDate +
                ", notes='" + notes + '\'' +
                ", itemsCount=" + items.size() +
                ", totalQuantity=" + getTotalReceivedQuantity() +
                '}';
    }
}
