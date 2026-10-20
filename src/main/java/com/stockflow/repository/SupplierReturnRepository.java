package com.stockflow.repository;

import com.stockflow.model.ReturnLineInput;
import com.stockflow.model.SupplierReturn;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Data access abstraction for managing supplier returns, return line items,
 * and stock reduction audit tracking.
 */
public interface SupplierReturnRepository {

    /**
     * Atomically records a supplier return against received goods for a purchase order:
     * - Validates purchase order and delivery receipt line eligibility
     * - Recalculates remaining returnable quantity from persisted return records
     * - Checks current available product stock to prevent negative inventory
     * - Reduces product inventory stock
     * - Records STOCK_OUT inventory audit transaction
     * - Inserts supplier return header and line items linked to receipt items and transactions
     *
     * @param orderId ID of the purchase order being returned against
     * @param returnNumber unique return number
     * @param reason optional return reason or notes
     * @param lines list of receipt line inputs and return quantities
     * @return the created SupplierReturn
     */
    SupplierReturn recordReturn(Long orderId, String returnNumber, String reason, List<ReturnLineInput> lines);

    /**
     * Retrieves all supplier returns for a specific purchase order, including their line items.
     * Ordered by return date descending, then ID descending.
     *
     * @param orderId ID of the purchase order
     * @return list of supplier returns
     */
    List<SupplierReturn> findReturnsByOrderId(Long orderId);

    /**
     * Finds a supplier return by its unique return number, including its line items.
     *
     * @param returnNumber unique return number
     * @return optional containing the return if found
     */
    Optional<SupplierReturn> findByReturnNumber(String returnNumber);

    /**
     * Finds a supplier return by primary key ID, including its line items.
     *
     * @param id primary key ID
     * @return optional containing the return if found
     */
    Optional<SupplierReturn> findById(Long id);

    /**
     * Returns a mapping of purchase_order_receipt_item_id to cumulative previously returned quantity
     * for all receipt lines of a purchase order.
     *
     * @param orderId ID of the purchase order
     * @return map of receipt item ID to total returned quantity
     */
    Map<Long, Integer> getReturnedQuantitiesForOrder(Long orderId);

    /**
     * Returns cumulative previously returned quantities for specific receipt item IDs.
     *
     * @param receiptItemIds list of receipt item IDs
     * @return map of receipt item ID to total returned quantity
     */
    Map<Long, Integer> getReturnedQuantitiesForReceiptItems(List<Long> receiptItemIds);

    /**
     * Checks if a supplier return exists with the specified return number.
     *
     * @param returnNumber unique return number
     * @return true if exists, false otherwise
     */
    boolean existsByReturnNumber(String returnNumber);

    /**
     * Checks if any supplier return item references the specified product ID.
     * Used for referential integrity and deletion protections.
     *
     * @param productId product primary key ID
     * @return true if references exist, false otherwise
     */
    boolean existsByProductId(Long productId);
}
