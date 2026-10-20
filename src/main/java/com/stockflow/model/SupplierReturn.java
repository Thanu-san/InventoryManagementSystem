package com.stockflow.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a supplier return of goods previously received against a purchase order.
 * Maintains header details (unique return number, purchase order ID, timestamp, optional reason)
 * and an unmodifiable collection of return line items.
 */
public class SupplierReturn {

    private Long id;
    private final String returnNumber;
    private final Long purchaseOrderId;
    private final LocalDateTime returnDate;
    private final String reason;
    private final List<SupplierReturnItem> items = new ArrayList<>();

    /**
     * Constructor before persistence.
     */
    public SupplierReturn(String returnNumber, Long purchaseOrderId, String reason) {
        this(null, returnNumber, purchaseOrderId, LocalDateTime.now(), reason, null);
    }

    /**
     * Full constructor for reconstituting an existing supplier return from storage.
     */
    public SupplierReturn(Long id, String returnNumber, Long purchaseOrderId,
                          LocalDateTime returnDate, String reason,
                          List<SupplierReturnItem> items) {
        if (returnNumber == null || returnNumber.isBlank()) {
            throw new IllegalArgumentException("Return number cannot be blank.");
        }
        if (purchaseOrderId == null || purchaseOrderId <= 0) {
            throw new IllegalArgumentException("Purchase order ID must be greater than zero.");
        }

        if (id != null) {
            setId(id);
        }
        this.returnNumber = returnNumber.trim();
        this.purchaseOrderId = purchaseOrderId;
        this.returnDate = returnDate != null ? returnDate : LocalDateTime.now();
        this.reason = (reason == null || reason.isBlank()) ? null : reason.trim();

        if (items != null) {
            for (SupplierReturnItem item : items) {
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
            throw new IllegalStateException("Supplier return ID is already assigned and cannot be changed.");
        }
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Supplier return ID must be greater than zero.");
        }
        this.id = id;
    }

    public String getReturnNumber() {
        return returnNumber;
    }

    public Long getPurchaseOrderId() {
        return purchaseOrderId;
    }

    public LocalDateTime getReturnDate() {
        return returnDate;
    }

    public String getReason() {
        return reason;
    }

    public List<SupplierReturnItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public void addItem(SupplierReturnItem item) {
        if (item == null) {
            throw new IllegalArgumentException("Return item cannot be null.");
        }
        if (this.id != null && item.getSupplierReturnId() == null) {
            item.setSupplierReturnId(this.id);
        }
        items.add(item);
    }

    public int getTotalReturnedUnits() {
        int total = 0;
        for (SupplierReturnItem item : items) {
            total += item.getQuantity();
        }
        return total;
    }

    public Optional<SupplierReturnItem> findItemById(Long itemId) {
        if (itemId == null) {
            return Optional.empty();
        }
        return items.stream()
                .filter(item -> itemId.equals(item.getId()))
                .findFirst();
    }

    public Optional<SupplierReturnItem> findItemByReceiptItemId(Long receiptItemId) {
        if (receiptItemId == null) {
            return Optional.empty();
        }
        return items.stream()
                .filter(item -> receiptItemId.equals(item.getPurchaseOrderReceiptItemId()))
                .findFirst();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SupplierReturn that = (SupplierReturn) o;
        if (id != null && that.id != null) {
            return Objects.equals(id, that.id);
        }
        return Objects.equals(returnNumber, that.returnNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(returnNumber);
    }

    @Override
    public String toString() {
        return "SupplierReturn{" +
                "id=" + id +
                ", returnNumber='" + returnNumber + '\'' +
                ", purchaseOrderId=" + purchaseOrderId +
                ", returnDate=" + returnDate +
                ", reason='" + reason + '\'' +
                ", itemsCount=" + items.size() +
                ", totalUnits=" + getTotalReturnedUnits() +
                '}';
    }
}
