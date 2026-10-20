package com.stockflow.model;

/**
 * Represents the lifecycle status of a purchase order in StockFlow.
 * Supports draft preparation, placement, partial deliveries, full fulfillment, and cancellation.
 */
public enum PurchaseOrderStatus {
    DRAFT,
    PLACED,
    PARTIALLY_RECEIVED,
    RECEIVED,
    CANCELLED
}
