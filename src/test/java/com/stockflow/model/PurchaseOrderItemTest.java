package com.stockflow.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PurchaseOrderItem Model Tests")
class PurchaseOrderItemTest {

    @Test
    @DisplayName("Should create valid purchase order item and calculate line total")
    void shouldCreateValidItemAndCalculateLineTotal() {
        PurchaseOrderItem item = new PurchaseOrderItem(10L, "SKU-001", "Keyboard", 5, new BigDecimal("25.50"));

        assertNull(item.getId());
        assertNull(item.getPurchaseOrderId());
        assertEquals(10L, item.getProductId());
        assertEquals("SKU-001", item.getProductSku());
        assertEquals("Keyboard", item.getProductName());
        assertEquals(5, item.getOrderedQuantity());
        assertEquals(0, item.getReceivedQuantity());
        assertEquals(5, item.getRemainingQuantity());
        assertFalse(item.isFullyReceived());
        assertEquals(new BigDecimal("25.50"), item.getUnitCost());
        assertEquals(new BigDecimal("127.50"), item.getLineTotal());
    }

    @Test
    @DisplayName("Should track received quantity and compute remaining accurately")
    void shouldTrackReceivedQuantityAndComputeRemaining() {
        PurchaseOrderItem item = new PurchaseOrderItem(10L, "SKU-001", "Keyboard", 10, new BigDecimal("20.00"));

        assertEquals(10, item.getRemainingQuantity());
        assertFalse(item.isFullyReceived());

        item.setReceivedQuantity(4);
        assertEquals(4, item.getReceivedQuantity());
        assertEquals(6, item.getRemainingQuantity());
        assertFalse(item.isFullyReceived());

        item.addReceivedQuantity(6);
        assertEquals(10, item.getReceivedQuantity());
        assertEquals(0, item.getRemainingQuantity());
        assertTrue(item.isFullyReceived());

        assertThrows(IllegalArgumentException.class, () -> item.setReceivedQuantity(-1));
        assertThrows(IllegalArgumentException.class, () -> item.addReceivedQuantity(0));
        assertThrows(IllegalArgumentException.class, () -> item.addReceivedQuantity(-5));
    }

    @Test
    @DisplayName("Should assign ID only once")
    void shouldAssignIdOnlyOnce() {
        PurchaseOrderItem item = new PurchaseOrderItem(10L, "SKU-001", "Keyboard", 1, new BigDecimal("10.00"));

        item.setId(100L);
        assertEquals(100L, item.getId());

        assertThrows(IllegalStateException.class, () -> item.setId(101L));
        assertThrows(IllegalStateException.class, () -> item.setId(100L));
    }

    @Test
    @DisplayName("Should reject invalid IDs")
    void shouldRejectInvalidIds() {
        PurchaseOrderItem item = new PurchaseOrderItem(10L, "SKU-001", "Keyboard", 1, new BigDecimal("10.00"));

        assertThrows(IllegalArgumentException.class, () -> item.setId(null));
        assertThrows(IllegalArgumentException.class, () -> item.setId(0L));
        assertThrows(IllegalArgumentException.class, () -> item.setId(-1L));
    }

    @Test
    @DisplayName("Should reject invalid constructor parameters")
    void shouldRejectInvalidConstructorParameters() {
        assertThrows(IllegalArgumentException.class, () ->
                new PurchaseOrderItem(null, "SKU-1", "Name", 1, new BigDecimal("10.00")));
        assertThrows(IllegalArgumentException.class, () ->
                new PurchaseOrderItem(0L, "SKU-1", "Name", 1, new BigDecimal("10.00")));
        assertThrows(IllegalArgumentException.class, () ->
                new PurchaseOrderItem(-5L, "SKU-1", "Name", 1, new BigDecimal("10.00")));
        assertThrows(IllegalArgumentException.class, () ->
                new PurchaseOrderItem(1L, null, "Name", 1, new BigDecimal("10.00")));
        assertThrows(IllegalArgumentException.class, () ->
                new PurchaseOrderItem(1L, "   ", "Name", 1, new BigDecimal("10.00")));
        assertThrows(IllegalArgumentException.class, () ->
                new PurchaseOrderItem(1L, "SKU-1", null, 1, new BigDecimal("10.00")));
        assertThrows(IllegalArgumentException.class, () ->
                new PurchaseOrderItem(1L, "SKU-1", "   ", 1, new BigDecimal("10.00")));
        assertThrows(IllegalArgumentException.class, () ->
                new PurchaseOrderItem(1L, "SKU-1", "Name", 0, new BigDecimal("10.00")));
        assertThrows(IllegalArgumentException.class, () ->
                new PurchaseOrderItem(1L, "SKU-1", "Name", -3, new BigDecimal("10.00")));
        assertThrows(IllegalArgumentException.class, () ->
                new PurchaseOrderItem(1L, "SKU-1", "Name", 1, null));
        assertThrows(IllegalArgumentException.class, () ->
                new PurchaseOrderItem(1L, "SKU-1", "Name", 1, new BigDecimal("-0.01")));
    }

    @Test
    @DisplayName("Should allow zero unit cost")
    void shouldAllowZeroUnitCost() {
        PurchaseOrderItem item = new PurchaseOrderItem(10L, "SKU-FREE", "Promotional Item", 3, BigDecimal.ZERO);
        assertEquals(BigDecimal.ZERO, item.getUnitCost());
        assertEquals(BigDecimal.ZERO, item.getLineTotal());
    }

    @Test
    @DisplayName("Should update quantity and unit cost through guarded setters")
    void shouldUpdateQuantityAndUnitCost() {
        PurchaseOrderItem item = new PurchaseOrderItem(10L, "SKU-001", "Keyboard", 2, new BigDecimal("20.00"));
        assertEquals(new BigDecimal("40.00"), item.getLineTotal());

        item.setOrderedQuantity(5);
        item.setUnitCost(new BigDecimal("18.50"));

        assertEquals(5, item.getOrderedQuantity());
        assertEquals(new BigDecimal("18.50"), item.getUnitCost());
        assertEquals(new BigDecimal("92.50"), item.getLineTotal());

        assertThrows(IllegalArgumentException.class, () -> item.setOrderedQuantity(0));
        assertThrows(IllegalArgumentException.class, () -> item.setOrderedQuantity(-1));
        assertThrows(IllegalArgumentException.class, () -> item.setUnitCost(null));
        assertThrows(IllegalArgumentException.class, () -> item.setUnitCost(new BigDecimal("-1.00")));
    }
}
