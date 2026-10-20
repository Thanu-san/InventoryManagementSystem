package com.stockflow.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReceivingHistoryReportItem Model Tests")
class ReceivingHistoryReportItemTest {

    @Test
    @DisplayName("Should create valid report item with all fields")
    void shouldCreateValidReportItem() {
        LocalDateTime now = LocalDateTime.now();
        ReceivingHistoryReportItem item = new ReceivingHistoryReportItem(
                "REC-001",
                "PO-1001",
                "Acme Corp",
                now,
                "SKU-100",
                "Widget",
                10,
                new BigDecimal("15.50"),
                new BigDecimal("155.00")
        );

        assertEquals("REC-001", item.getReceiptNumber());
        assertEquals("PO-1001", item.getOrderNumber());
        assertEquals("Acme Corp", item.getSupplierName());
        assertEquals(now, item.getReceiptDate());
        assertEquals("SKU-100", item.getProductSku());
        assertEquals("Widget", item.getProductName());
        assertEquals(10, item.getReceivedQuantity());
        assertEquals(new BigDecimal("15.50"), item.getAgreedUnitCost());
        assertEquals(new BigDecimal("155.00"), item.getLineTotalCost());
    }

    @Test
    @DisplayName("Should reject blank strings and nulls")
    void shouldRejectBlankStringsAndNulls() {
        LocalDateTime now = LocalDateTime.now();
        BigDecimal cost = new BigDecimal("10.00");
        BigDecimal total = new BigDecimal("100.00");

        assertThrows(IllegalArgumentException.class, () ->
                new ReceivingHistoryReportItem(" ", "PO-1", "Acme", now, "SKU", "Name", 10, cost, total));
        assertThrows(IllegalArgumentException.class, () ->
                new ReceivingHistoryReportItem("REC-1", " ", "Acme", now, "SKU", "Name", 10, cost, total));
        assertThrows(IllegalArgumentException.class, () ->
                new ReceivingHistoryReportItem("REC-1", "PO-1", " ", now, "SKU", "Name", 10, cost, total));
        assertThrows(IllegalArgumentException.class, () ->
                new ReceivingHistoryReportItem("REC-1", "PO-1", "Acme", null, "SKU", "Name", 10, cost, total));
        assertThrows(IllegalArgumentException.class, () ->
                new ReceivingHistoryReportItem("REC-1", "PO-1", "Acme", now, " ", "Name", 10, cost, total));
        assertThrows(IllegalArgumentException.class, () ->
                new ReceivingHistoryReportItem("REC-1", "PO-1", "Acme", now, "SKU", " ", 10, cost, total));
    }

    @Test
    @DisplayName("Should reject invalid quantities and negative costs")
    void shouldRejectInvalidQuantitiesAndNegativeCosts() {
        LocalDateTime now = LocalDateTime.now();
        BigDecimal cost = new BigDecimal("10.00");
        BigDecimal total = new BigDecimal("100.00");

        assertThrows(IllegalArgumentException.class, () ->
                new ReceivingHistoryReportItem("REC-1", "PO-1", "Acme", now, "SKU", "Name", 0, cost, total));
        assertThrows(IllegalArgumentException.class, () ->
                new ReceivingHistoryReportItem("REC-1", "PO-1", "Acme", now, "SKU", "Name", -5, cost, total));
        assertThrows(IllegalArgumentException.class, () ->
                new ReceivingHistoryReportItem("REC-1", "PO-1", "Acme", now, "SKU", "Name", 10, null, total));
        assertThrows(IllegalArgumentException.class, () ->
                new ReceivingHistoryReportItem("REC-1", "PO-1", "Acme", now, "SKU", "Name", 10, new BigDecimal("-1.00"), total));
        assertThrows(IllegalArgumentException.class, () ->
                new ReceivingHistoryReportItem("REC-1", "PO-1", "Acme", now, "SKU", "Name", 10, cost, null));
        assertThrows(IllegalArgumentException.class, () ->
                new ReceivingHistoryReportItem("REC-1", "PO-1", "Acme", now, "SKU", "Name", 10, cost, new BigDecimal("-1.00")));
    }

    @Test
    @DisplayName("Should verify equals, hashCode, and toString")
    void shouldVerifyEqualsHashCodeToString() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 20, 10, 0, 0);
        ReceivingHistoryReportItem item1 = new ReceivingHistoryReportItem(
                "REC-1", "PO-1", "Acme", now, "SKU-1", "Widget", 5, new BigDecimal("10.00"), new BigDecimal("50.00"));
        ReceivingHistoryReportItem item2 = new ReceivingHistoryReportItem(
                "REC-1", "PO-1", "Acme", now, "SKU-1", "Widget", 5, new BigDecimal("10.00"), new BigDecimal("50.00"));
        ReceivingHistoryReportItem item3 = new ReceivingHistoryReportItem(
                "REC-2", "PO-1", "Acme", now, "SKU-1", "Widget", 5, new BigDecimal("10.00"), new BigDecimal("50.00"));

        assertEquals(item1, item2);
        assertEquals(item1.hashCode(), item2.hashCode());
        assertNotEquals(item1, item3);
        assertTrue(item1.toString().contains("REC-1"));
        assertTrue(item1.toString().contains("SKU-1"));
    }
}
