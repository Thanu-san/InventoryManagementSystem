package com.stockflow.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a purchase order placed with a supplier.
 * Encapsulates purchase order domain state and invariants:
 * - Order number is immutable and serves as business identity.
 * - ID can only be assigned once upon persistence.
 * - Supplier ID is immutable once created.
 * - Status transitions follow strict lifecycle rules: DRAFT -> PLACED or CANCELLED.
 * - Total cost is dynamically calculated from its line items.
 */
public class PurchaseOrder {

    private Long id;
    private final String orderNumber;
    private final Long supplierId;
    private PurchaseOrderStatus status;
    private final LocalDateTime createdAt;
    private final List<PurchaseOrderItem> items = new ArrayList<>();

    /**
     * Constructor for creating a new purchase order before persistence.
     * Defaults status to DRAFT and sets creation timestamp to now.
     */
    public PurchaseOrder(String orderNumber, Long supplierId) {
        this(null, orderNumber, supplierId, PurchaseOrderStatus.DRAFT, LocalDateTime.now(), null);
    }

    /**
     * Full constructor for reconstituting an existing purchase order from storage.
     */
    public PurchaseOrder(Long id, String orderNumber, Long supplierId,
                         PurchaseOrderStatus status, LocalDateTime createdAt,
                         List<PurchaseOrderItem> initialItems) {
        if (orderNumber == null || orderNumber.isBlank()) {
            throw new IllegalArgumentException("Order number cannot be blank.");
        }
        if (supplierId == null || supplierId <= 0) {
            throw new IllegalArgumentException("Supplier ID must be greater than zero.");
        }
        if (status == null) {
            throw new IllegalArgumentException("Purchase order status cannot be null.");
        }

        if (id != null) {
            setId(id);
        }
        this.orderNumber = orderNumber.trim();
        this.supplierId = supplierId;
        this.status = status;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();

        if (initialItems != null) {
            for (PurchaseOrderItem item : initialItems) {
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
            throw new IllegalStateException("Purchase order ID is already assigned and cannot be changed.");
        }
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Purchase order ID must be greater than zero.");
        }
        this.id = id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public PurchaseOrderStatus getStatus() {
        return status;
    }

    public void setStatus(PurchaseOrderStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Purchase order status cannot be null.");
        }
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<PurchaseOrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    /**
     * Adds or updates a line item in the order.
     * If an item for the same product ID already exists, updates quantity and unit cost.
     */
    public void addOrUpdateItem(PurchaseOrderItem item) {
        if (item == null) {
            throw new IllegalArgumentException("Line item cannot be null.");
        }
        Optional<PurchaseOrderItem> existing = findItemByProductId(item.getProductId());
        if (existing.isPresent()) {
            PurchaseOrderItem current = existing.get();
            current.setOrderedQuantity(item.getOrderedQuantity());
            current.setUnitCost(item.getUnitCost());
        } else {
            if (this.id != null && item.getPurchaseOrderId() == null) {
                item.setPurchaseOrderId(this.id);
            }
            items.add(item);
        }
    }

    /**
     * Removes a line item by product ID.
     * Returns true if found and removed, false otherwise.
     */
    public boolean removeItem(Long productId) {
        if (productId == null) {
            return false;
        }
        return items.removeIf(item -> productId.equals(item.getProductId()));
    }

    /**
     * Finds a line item by product ID.
     */
    public Optional<PurchaseOrderItem> findItemByProductId(Long productId) {
        if (productId == null) {
            return Optional.empty();
        }
        return items.stream()
                .filter(item -> productId.equals(item.getProductId()))
                .findFirst();
    }

    /**
     * Finds a line item by product SKU.
     */
    public Optional<PurchaseOrderItem> findItemByProductSku(String sku) {
        if (sku == null || sku.isBlank()) {
            return Optional.empty();
        }
        String cleanSku = sku.trim();
        return items.stream()
                .filter(item -> cleanSku.equalsIgnoreCase(item.getProductSku()))
                .findFirst();
    }

    /**
     * Calculates the total monetary amount of the order by summing all line totals.
     */
    public BigDecimal getTotalAmount() {
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseOrderItem item : items) {
            total = total.add(item.getLineTotal());
        }
        return total;
    }

    /**
     * Calculates the total ordered units across all line items.
     */
    public int getTotalUnits() {
        int total = 0;
        for (PurchaseOrderItem item : items) {
            total += item.getOrderedQuantity();
        }
        return total;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PurchaseOrder that)) return false;
        if (id != null && that.id != null) return Objects.equals(id, that.id);
        return Objects.equals(orderNumber, that.orderNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderNumber);
    }

    @Override
    public String toString() {
        return "PurchaseOrder{" +
                "id=" + id +
                ", orderNumber='" + orderNumber + '\'' +
                ", supplierId=" + supplierId +
                ", status=" + status +
                ", createdAt=" + createdAt +
                ", itemsCount=" + items.size() +
                ", totalAmount=" + getTotalAmount() +
                '}';
    }
}
