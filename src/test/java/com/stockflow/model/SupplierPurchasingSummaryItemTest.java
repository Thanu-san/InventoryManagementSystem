package com.stockflow.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SupplierPurchasingSummaryItem Model Tests")
class SupplierPurchasingSummaryItemTest {

    @Test
    @DisplayName("Should create valid summary item with all fields")
    void shouldCreateValidSummaryItem() {
        SupplierPurchasingSummaryItem item = new SupplierPurchasingSummaryItem(
                "SUP-01",
                "Acme Corp",
                5,
                100,
                70,
                30,
                new BigDecimal("1200.00"),
                new BigDecimal("840.00")
        );

        assertEquals("SUP-01", item.getSupplierCode());
        assertEquals("Acme Corp", item.getSupplierName());
        assertEquals(5, item.getOrderCount());
        assertEquals(100, item.getTotalOrderedUnits());
        assertEquals(70, item.getTotalReceivedUnits());
        assertEquals(30, item.getTotalOutstandingUnits());
        assertEquals(new BigDecimal("1200.00"), item.getTotalOrderValue());
        assertEquals(new BigDecimal("840.00"), item.getTotalReceivedValue());
    }

    @Test
    @DisplayName("Should reject blank supplier code or name")
    void shouldRejectBlankSupplierCodeOrName() {
        assertThrows(IllegalArgumentException.class, () ->
                new SupplierPurchasingSummaryItem(" ", "Acme", 1, 10, 0, 10, BigDecimal.TEN, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () ->
                new SupplierPurchasingSummaryItem("SUP-01", "  ", 1, 10, 0, 10, BigDecimal.TEN, BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Should reject negative quantities and values")
    void shouldRejectNegativeQuantitiesAndValues() {
        assertThrows(IllegalArgumentException.class, () ->
                new SupplierPurchasingSummaryItem("SUP-01", "Acme", -1, 10, 0, 10, BigDecimal.TEN, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () ->
                new SupplierPurchasingSummaryItem("SUP-01", "Acme", 1, -10, 0, 10, BigDecimal.TEN, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () ->
                new SupplierPurchasingSummaryItem("SUP-01", "Acme", 1, 10, -5, 10, BigDecimal.TEN, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () ->
                new SupplierPurchasingSummaryItem("SUP-01", "Acme", 1, 10, 0, -10, BigDecimal.TEN, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () ->
                new SupplierPurchasingSummaryItem("SUP-01", "Acme", 1, 10, 0, 10, null, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () ->
                new SupplierPurchasingSummaryItem("SUP-01", "Acme", 1, 10, 0, 10, new BigDecimal("-1.00"), BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () ->
                new SupplierPurchasingSummaryItem("SUP-01", "Acme", 1, 10, 0, 10, BigDecimal.TEN, null));
        assertThrows(IllegalArgumentException.class, () ->
                new SupplierPurchasingSummaryItem("SUP-01", "Acme", 1, 10, 0, 10, BigDecimal.TEN, new BigDecimal("-1.00")));
    }

    @Test
    @DisplayName("Should verify equals, hashCode, and toString")
    void shouldVerifyEqualsHashCodeToString() {
        SupplierPurchasingSummaryItem item1 = new SupplierPurchasingSummaryItem(
                "SUP-01", "Acme", 2, 20, 10, 10, new BigDecimal("200.00"), new BigDecimal("100.00"));
        SupplierPurchasingSummaryItem item2 = new SupplierPurchasingSummaryItem(
                "SUP-01", "Acme", 2, 20, 10, 10, new BigDecimal("200.00"), new BigDecimal("100.00"));
        SupplierPurchasingSummaryItem item3 = new SupplierPurchasingSummaryItem(
                "SUP-02", "Beta", 2, 20, 10, 10, new BigDecimal("200.00"), new BigDecimal("100.00"));

        assertEquals(item1, item2);
        assertEquals(item1.hashCode(), item2.hashCode());
        assertNotEquals(item1, item3);
        assertTrue(item1.toString().contains("SUP-01"));
        assertTrue(item1.toString().contains("Acme"));
    }
}
