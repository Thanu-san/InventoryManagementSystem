package com.stockflow.repository;

import com.stockflow.model.OutstandingPurchaseOrderReportItem;
import com.stockflow.model.ReceivingHistoryReportItem;
import com.stockflow.model.SupplierPurchasingSummaryItem;

import java.time.LocalDate;
import java.util.List;

/**
 * Data access abstraction for purchasing and receiving analytical reports.
 * All reporting methods are strictly read-only and must never mutate domain records.
 */
public interface PurchaseOrderReportRepository {

    /**
     * Retrieves all purchase orders that have outstanding goods to be received (status PLACED or PARTIALLY_RECEIVED).
     * Excludes DRAFT, RECEIVED, and CANCELLED orders.
     * Ordered by order creation date ascending (oldest first), then order number ascending.
     *
     * @return unmodifiable list of outstanding purchase order report items
     */
    List<OutstandingPurchaseOrderReportItem> getOutstandingPurchaseOrdersReport();

    /**
     * Retrieves receiving history lines recorded within the given inclusive date range [startDate, endDate].
     * Uses historical purchase order item agreed unit costs.
     * Ordered chronologically by receipt date ascending, then receipt number ascending, then line item ID ascending.
     *
     * @param startDate inclusive start date
     * @param endDate inclusive end date
     * @return unmodifiable list of receiving history report items
     */
    List<ReceivingHistoryReportItem> getReceivingHistoryReport(LocalDate startDate, LocalDate endDate);

    /**
     * Retrieves committed purchasing performance and receiving metrics grouped by supplier.
     * Excludes CANCELLED orders from commitments.
     * Ordered by supplier code ascending.
     *
     * @return unmodifiable list of supplier purchasing summary items
     */
    List<SupplierPurchasingSummaryItem> getSupplierPurchasingSummary();
}
