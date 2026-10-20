package com.stockflow.repository;

import com.stockflow.model.PurchaseOrderReceipt;
import com.stockflow.model.ReceiptLineInput;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository interface for managing purchase order receiving operations,
 * receipt headers, and receipt line items linked to inventory transactions.
 */
public interface PurchaseOrderReceiptRepository {

    /**
     * Atomically records a delivery receipt against a purchase order:
     * - Validates order status and line remaining quantities
     * - Inserts receipt header and receipt items
     * - Increases product stock quantities
     * - Records STOCK_IN inventory audit transactions
     * - Links receipt items to inventory transactions
     * - Updates purchase order status to PARTIALLY_RECEIVED or RECEIVED
     *
     * @param orderId the ID of the purchase order being received
     * @param receiptNumber unique receipt number
     * @param notes optional receipt notes
     * @param lines list of line items and received quantities
     * @return the created PurchaseOrderReceipt
     */
    PurchaseOrderReceipt recordReceipt(Long orderId, String receiptNumber, String notes, List<ReceiptLineInput> lines);

    /**
     * Retrieves all delivery receipts for a specific purchase order, including their receipt items.
     */
    List<PurchaseOrderReceipt> findReceiptsByOrderId(Long orderId);

    /**
     * Finds a receipt by its unique receipt number.
     */
    Optional<PurchaseOrderReceipt> findByReceiptNumber(String receiptNumber);

    /**
     * Returns a mapping of purchase_order_item_id to cumulative received quantity for a purchase order.
     */
    Map<Long, Integer> getReceivedQuantitiesForOrder(Long orderId);

    /**
     * Returns cumulative received quantities for multiple purchase orders.
     */
    Map<Long, Map<Long, Integer>> getReceivedQuantitiesForOrders(List<Long> orderIds);
}
