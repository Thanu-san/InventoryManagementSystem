package com.stockflow.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PurchaseOrderReceipt Model Tests")
class PurchaseOrderReceiptTest {

    @Test
    @DisplayName("Should create valid receipt and calculate total received quantity")
    void shouldCreateValidReceiptAndCalculateTotal() {
        PurchaseOrderReceipt receipt = new PurchaseOrderReceipt("REC-001", 50L, "First delivery");

        assertNull(receipt.getId());
        assertEquals("REC-001", receipt.getReceiptNumber());
        assertEquals(50L, receipt.getPurchaseOrderId());
        assertEquals("First delivery", receipt.getNotes());
        assertNotNull(receipt.getReceiptDate());
        assertTrue(receipt.getItems().isEmpty());
        assertEquals(0, receipt.getTotalReceivedQuantity());

        receipt.addItem(new PurchaseOrderReceiptItem(1L, 10L, 5));
        receipt.addItem(new PurchaseOrderReceiptItem(2L, 20L, 15));

        assertEquals(2, receipt.getItems().size());
        assertEquals(20, receipt.getTotalReceivedQuantity());
    }

    @Test
    @DisplayName("Should assign ID only once")
    void shouldAssignIdOnce() {
        PurchaseOrderReceipt receipt = new PurchaseOrderReceipt("REC-001", 50L, null);

        receipt.setId(10L);
        assertEquals(10L, receipt.getId());
        assertThrows(IllegalStateException.class, () -> receipt.setId(11L));
    }

    @Test
    @DisplayName("Should reject invalid constructor parameters")
    void shouldRejectInvalidParameters() {
        assertThrows(IllegalArgumentException.class, () -> new PurchaseOrderReceipt(null, 1L, null));
        assertThrows(IllegalArgumentException.class, () -> new PurchaseOrderReceipt("   ", 1L, null));
        assertThrows(IllegalArgumentException.class, () -> new PurchaseOrderReceipt("REC-1", null, null));
        assertThrows(IllegalArgumentException.class, () -> new PurchaseOrderReceipt("REC-1", 0L, null));
        assertThrows(IllegalArgumentException.class, () -> new PurchaseOrderReceipt("REC-1", -1L, null));
    }
}
