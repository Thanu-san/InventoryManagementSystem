package com.stockflow.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OutstandingPurchaseOrderReportItem Model Tests")
class OutstandingPurchaseOrderReportItemTest {

    @Test
    @DisplayName("Should create valid report item with all fields")
    void shouldCreateValidReportItem() {
        LocalDateTime now = LocalDateTime.now();
        OutstandingPurchaseOrderReportItem item = new OutstandingPurchaseOrderReportItem(
                "PO-1001",
                "SUP-01",
                "Acme Corp",
                now,
                3,
                50,
                20,
                30,
                PurchaseOrderStatus.PARTIALLY_RECEIVED
        );

        assertEquals("PO-1001", item.getOrderNumber());
        assertEquals("SUP-01", item.getSupplierCode());
        assertEquals("Acme Corp", item.getSupplierName());
        assertEquals(now, item.getCreatedAt());
        assertEquals(3, item.getDistinctLineCount());
        assertEquals(50, item.getOrderedUnits());
        assertEquals(20, item.getReceivedUnits());
        assertEquals(30, item.getRemainingUnits());
        assertEquals(PurchaseOrderStatus.PARTIALLY_RECEIVED, item.getStatus());
    }

    @Test
    @DisplayName("Should reject blank order number")
    void shouldRejectBlankOrderNumber() {
        assertThrows(IllegalArgumentException.class, () ->
                new OutstandingPurchaseOrderReportItem("  ", "SUP-01", "Acme", LocalDateTime.now(), 1, 10, 0, 10, PurchaseOrderStatus.PLACED));
    }

    @Test
    @DisplayName("Should reject blank supplier code")
    void shouldRejectBlankSupplierCode() {
        assertThrows(IllegalArgumentException.class, () ->
                new OutstandingPurchaseOrderReportItem("PO-1", "  ", "Acme", LocalDateTime.now(), 1, 10, 0, 10, PurchaseOrderStatus.PLACED));
    }

    @Test
    @DisplayName("Should reject blank supplier name")
    void shouldRejectBlankSupplierName() {
        assertThrows(IllegalArgumentException.class, () ->
                new OutstandingPurchaseOrderReportItem("PO-1", "SUP-01", " ", LocalDateTime.now(), 1, 10, 0, 10, PurchaseOrderStatus.PLACED));
    }

    @Test
    @DisplayName("Should reject null creation date")
    void shouldRejectNullCreationDate() {
        assertThrows(IllegalArgumentException.class, () ->
                new OutstandingPurchaseOrderReportItem("PO-1", "SUP-01", "Acme", null, 1, 10, 0, 10, PurchaseOrderStatus.PLACED));
    }

    @Test
    @DisplayName("Should reject negative quantities")
    void shouldRejectNegativeQuantities() {
        LocalDateTime now = LocalDateTime.now();
        assertThrows(IllegalArgumentException.class, () ->
                new OutstandingPurchaseOrderReportItem("PO-1", "SUP-01", "Acme", now, -1, 10, 0, 10, PurchaseOrderStatus.PLACED));
        assertThrows(IllegalArgumentException.class, () ->
                new OutstandingPurchaseOrderReportItem("PO-1", "SUP-01", "Acme", now, 1, -5, 0, 10, PurchaseOrderStatus.PLACED));
        assertThrows(IllegalArgumentException.class, () ->
                new OutstandingPurchaseOrderReportItem("PO-1", "SUP-01", "Acme", now, 1, 10, -1, 10, PurchaseOrderStatus.PLACED));
        assertThrows(IllegalArgumentException.class, () ->
                new OutstandingPurchaseOrderReportItem("PO-1", "SUP-01", "Acme", now, 1, 10, 0, -5, PurchaseOrderStatus.PLACED));
    }

    @Test
    @DisplayName("Should reject null status")
    void shouldRejectNullStatus() {
        assertThrows(IllegalArgumentException.class, () ->
                new OutstandingPurchaseOrderReportItem("PO-1", "SUP-01", "Acme", LocalDateTime.now(), 1, 10, 0, 10, null));
    }

    @Test
    @DisplayName("Should verify equals, hashCode, and toString")
    void shouldVerifyEqualsHashCodeToString() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 20, 10, 0, 0);
        OutstandingPurchaseOrderReportItem item1 = new OutstandingPurchaseOrderReportItem(
                "PO-1", "SUP-1", "Acme", now, 2, 20, 5, 15, PurchaseOrderStatus.PARTIALLY_RECEIVED);
        OutstandingPurchaseOrderReportItem item2 = new OutstandingPurchaseOrderReportItem(
                "PO-1", "SUP-1", "Acme", now, 2, 20, 5, 15, PurchaseOrderStatus.PARTIALLY_RECEIVED);
        OutstandingPurchaseOrderReportItem item3 = new OutstandingPurchaseOrderReportItem(
                "PO-2", "SUP-1", "Acme", now, 2, 20, 5, 15, PurchaseOrderStatus.PARTIALLY_RECEIVED);

        assertEquals(item1, item2);
        assertEquals(item1.hashCode(), item2.hashCode());
        assertNotEquals(item1, item3);
        assertTrue(item1.toString().contains("PO-1"));
        assertTrue(item1.toString().contains("Acme"));
    }
}
