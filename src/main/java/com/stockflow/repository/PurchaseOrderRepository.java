package com.stockflow.repository;

import com.stockflow.model.PurchaseOrder;
import com.stockflow.model.PurchaseOrderItem;
import com.stockflow.model.PurchaseOrderStatus;

import java.util.List;
import java.util.Optional;

/**
 * Data access abstraction for PurchaseOrder and its line items.
 */
public interface PurchaseOrderRepository {

    /**
     * Saves a new purchase order.
     * Rejects orders that already have an assigned ID.
     */
    PurchaseOrder save(PurchaseOrder order);

    /**
     * Finds a purchase order by its primary key ID, including its line items.
     */
    Optional<PurchaseOrder> findById(Long id);

    /**
     * Finds a purchase order by its unique order number, including its line items.
     */
    Optional<PurchaseOrder> findByOrderNumber(String orderNumber);

    /**
     * Checks if a purchase order exists with the specified order number.
     */
    boolean existsByOrderNumber(String orderNumber);

    /**
     * Returns all purchase orders, including their line items, ordered by creation time descending.
     */
    List<PurchaseOrder> findAll();

    /**
     * Returns all purchase orders matching the specified status, including line items.
     */
    List<PurchaseOrder> findByStatus(PurchaseOrderStatus status);

    /**
     * Updates the status of an existing purchase order.
     * Returns true if updated, false if order not found.
     */
    boolean updateStatus(Long orderId, PurchaseOrderStatus status);

    /**
     * Adds or updates a line item for the specified purchase order.
     * If the item already exists for the product, its quantity and unit cost are updated.
     */
    PurchaseOrderItem saveOrUpdateItem(Long orderId, PurchaseOrderItem item);

    /**
     * Removes a line item for the specified product from a purchase order.
     * Returns true if an item was removed, false otherwise.
     */
    boolean removeItem(Long orderId, Long productId);

    /**
     * Checks if any purchase order references the specified supplier ID.
     */
    boolean existsBySupplierId(Long supplierId);

    /**
     * Checks if any purchase order item references the specified product ID.
     */
    boolean existsByProductId(Long productId);
}
