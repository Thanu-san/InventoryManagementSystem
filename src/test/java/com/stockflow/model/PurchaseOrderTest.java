package com.stockflow.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PurchaseOrder Model Tests")
class PurchaseOrderTest {

    @Test
    @DisplayName("Should create draft purchase order with default values")
    void shouldCreateDraftOrderWithDefaults() {
        PurchaseOrder order = new PurchaseOrder("PO-001", 5L);

        assertNull(order.getId());
        assertEquals("PO-001", order.getOrderNumber());
        assertEquals(5L, order.getSupplierId());
        assertEquals(PurchaseOrderStatus.DRAFT, order.getStatus());
        assertNotNull(order.getCreatedAt());
        assertTrue(order.getItems().isEmpty());
        assertEquals(BigDecimal.ZERO, order.getTotalAmount());
        assertEquals(0, order.getTotalUnits());
    }

    @Test
    @DisplayName("Should assign ID only once")
    void shouldAssignIdOnlyOnce() {
        PurchaseOrder order = new PurchaseOrder("PO-001", 5L);

        order.setId(50L);
        assertEquals(50L, order.getId());

        assertThrows(IllegalStateException.class, () -> order.setId(51L));
        assertThrows(IllegalStateException.class, () -> order.setId(50L));
    }

    @Test
    @DisplayName("Should reject invalid constructor parameters")
    void shouldRejectInvalidConstructorParameters() {
        assertThrows(IllegalArgumentException.class, () -> new PurchaseOrder(null, 5L));
        assertThrows(IllegalArgumentException.class, () -> new PurchaseOrder("   ", 5L));
        assertThrows(IllegalArgumentException.class, () -> new PurchaseOrder("PO-1", null));
        assertThrows(IllegalArgumentException.class, () -> new PurchaseOrder("PO-1", 0L));
        assertThrows(IllegalArgumentException.class, () -> new PurchaseOrder("PO-1", -1L));

        assertThrows(IllegalArgumentException.class, () ->
                new PurchaseOrder(1L, "PO-1", 5L, null, LocalDateTime.now(), null));
    }

    @Test
    @DisplayName("Should calculate total amount and total units accurately")
    void shouldCalculateTotalsAccurately() {
        PurchaseOrder order = new PurchaseOrder("PO-001", 5L);

        PurchaseOrderItem item1 = new PurchaseOrderItem(10L, "SKU-A", "Product A", 3, new BigDecimal("15.50"));
        PurchaseOrderItem item2 = new PurchaseOrderItem(20L, "SKU-B", "Product B", 2, new BigDecimal("20.00"));

        order.addOrUpdateItem(item1);
        order.addOrUpdateItem(item2);

        assertEquals(2, order.getItems().size());
        assertEquals(5, order.getTotalUnits());
        // 3 * 15.50 = 46.50 + 2 * 20.00 = 40.00 => 86.50
        assertEquals(new BigDecimal("86.50"), order.getTotalAmount());
    }

    @Test
    @DisplayName("Should update existing item when adding item with same product ID")
    void shouldUpdateExistingItemOnDuplicateProductId() {
        PurchaseOrder order = new PurchaseOrder("PO-001", 5L);

        PurchaseOrderItem item1 = new PurchaseOrderItem(10L, "SKU-A", "Product A", 2, new BigDecimal("10.00"));
        order.addOrUpdateItem(item1);
        assertEquals(1, order.getItems().size());
        assertEquals(2, order.getTotalUnits());
        assertEquals(new BigDecimal("20.00"), order.getTotalAmount());

        // Add item for same product ID with new quantity and cost
        PurchaseOrderItem itemUpdated = new PurchaseOrderItem(10L, "SKU-A", "Product A", 5, new BigDecimal("12.00"));
        order.addOrUpdateItem(itemUpdated);

        assertEquals(1, order.getItems().size(), "Item count should remain 1");
        assertEquals(5, order.getTotalUnits());
        assertEquals(new BigDecimal("60.00"), order.getTotalAmount());
    }

    @Test
    @DisplayName("Should remove item by product ID")
    void shouldRemoveItemByProductId() {
        PurchaseOrder order = new PurchaseOrder("PO-001", 5L);
        order.addOrUpdateItem(new PurchaseOrderItem(10L, "SKU-A", "Product A", 2, new BigDecimal("10.00")));
        order.addOrUpdateItem(new PurchaseOrderItem(20L, "SKU-B", "Product B", 3, new BigDecimal("5.00")));

        assertEquals(2, order.getItems().size());

        boolean removed = order.removeItem(10L);
        assertTrue(removed);
        assertEquals(1, order.getItems().size());
        assertFalse(order.findItemByProductId(10L).isPresent());
        assertTrue(order.findItemByProductId(20L).isPresent());

        assertFalse(order.removeItem(999L));
        assertFalse(order.removeItem(null));
    }

    @Test
    @DisplayName("Should find item by product SKU case-insensitively")
    void shouldFindItemByProductSku() {
        PurchaseOrder order = new PurchaseOrder("PO-001", 5L);
        order.addOrUpdateItem(new PurchaseOrderItem(10L, "SKU-KEY-01", "Keyboard", 2, new BigDecimal("25.00")));

        assertTrue(order.findItemByProductSku("SKU-KEY-01").isPresent());
        assertTrue(order.findItemByProductSku("sku-key-01").isPresent());
        assertTrue(order.findItemByProductSku("  SKU-KEY-01  ").isPresent());
        assertTrue(order.findItemByProductSku("NON-EXIST").isEmpty());
        assertTrue(order.findItemByProductSku(null).isEmpty());
        assertTrue(order.findItemByProductSku("").isEmpty());
    }

    @Test
    @DisplayName("Should transition status")
    void shouldTransitionStatus() {
        PurchaseOrder order = new PurchaseOrder("PO-001", 5L);
        assertEquals(PurchaseOrderStatus.DRAFT, order.getStatus());

        order.setStatus(PurchaseOrderStatus.PLACED);
        assertEquals(PurchaseOrderStatus.PLACED, order.getStatus());

        order.setStatus(PurchaseOrderStatus.CANCELLED);
        assertEquals(PurchaseOrderStatus.CANCELLED, order.getStatus());

        assertThrows(IllegalArgumentException.class, () -> order.setStatus(null));
    }
}
