package com.stockflow.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PurchaseOrderReceiptItem Model Tests")
class PurchaseOrderReceiptItemTest {

    @Test
    @DisplayName("Should create valid receipt item")
    void shouldCreateValidReceiptItem() {
        PurchaseOrderReceiptItem item = new PurchaseOrderReceiptItem(100L, 200L, 10);

        assertNull(item.getId());
        assertNull(item.getReceiptId());
        assertEquals(100L, item.getPurchaseOrderItemId());
        assertEquals(200L, item.getProductId());
        assertEquals(10, item.getQuantity());
        assertNull(item.getInventoryTransactionId());
    }

    @Test
    @DisplayName("Should assign ID and transaction ID once")
    void shouldAssignIdsOnce() {
        PurchaseOrderReceiptItem item = new PurchaseOrderReceiptItem(100L, 200L, 10);

        item.setId(5L);
        assertEquals(5L, item.getId());
        assertThrows(IllegalStateException.class, () -> item.setId(6L));

        item.setReceiptId(10L);
        assertEquals(10L, item.getReceiptId());
        assertThrows(IllegalStateException.class, () -> item.setReceiptId(11L));

        item.setInventoryTransactionId(55L);
        assertEquals(55L, item.getInventoryTransactionId());
        assertThrows(IllegalStateException.class, () -> item.setInventoryTransactionId(56L));
    }

    @Test
    @DisplayName("Should reject invalid parameters")
    void shouldRejectInvalidParameters() {
        assertThrows(IllegalArgumentException.class, () -> new PurchaseOrderReceiptItem(null, 1L, 5));
        assertThrows(IllegalArgumentException.class, () -> new PurchaseOrderReceiptItem(0L, 1L, 5));
        assertThrows(IllegalArgumentException.class, () -> new PurchaseOrderReceiptItem(1L, null, 5));
        assertThrows(IllegalArgumentException.class, () -> new PurchaseOrderReceiptItem(1L, 0L, 5));
        assertThrows(IllegalArgumentException.class, () -> new PurchaseOrderReceiptItem(1L, 1L, 0));
        assertThrows(IllegalArgumentException.class, () -> new PurchaseOrderReceiptItem(1L, 1L, -1));
    }
}
