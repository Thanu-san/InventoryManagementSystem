package com.stockflow.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SupplierReturnItem Model Tests")
class SupplierReturnItemTest {

    @Test
    @DisplayName("Should create valid return item with all fields")
    void shouldCreateValidReturnItem() {
        SupplierReturnItem item = new SupplierReturnItem(1L, 10L, 100L, 50L, 5, "Damaged in transit", 500L);

        assertEquals(1L, item.getId());
        assertEquals(10L, item.getSupplierReturnId());
        assertEquals(100L, item.getPurchaseOrderReceiptItemId());
        assertEquals(50L, item.getProductId());
        assertEquals(5, item.getQuantity());
        assertEquals("Damaged in transit", item.getReason());
        assertEquals(500L, item.getInventoryTransactionId());
    }

    @Test
    @DisplayName("Should reject invalid IDs and non-positive quantity")
    void shouldRejectInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () ->
                new SupplierReturnItem(null, 10L, 0L, 50L, 5, "Reason", 500L));
        assertThrows(IllegalArgumentException.class, () ->
                new SupplierReturnItem(null, 10L, 100L, -1L, 5, "Reason", 500L));
        assertThrows(IllegalArgumentException.class, () ->
                new SupplierReturnItem(null, 10L, 100L, 50L, 0, "Reason", 500L));
        assertThrows(IllegalArgumentException.class, () ->
                new SupplierReturnItem(null, 10L, 100L, 50L, -5, "Reason", 500L));
    }

    @Test
    @DisplayName("Should allow ID and transaction ID assignment only once")
    void shouldEnforceSingleIdAssignment() {
        SupplierReturnItem item = new SupplierReturnItem(100L, 50L, 5, "Defective");
        assertNull(item.getId());
        assertNull(item.getInventoryTransactionId());
        assertNull(item.getSupplierReturnId());

        item.setId(1L);
        assertEquals(1L, item.getId());
        assertThrows(IllegalStateException.class, () -> item.setId(2L));

        item.setSupplierReturnId(10L);
        assertEquals(10L, item.getSupplierReturnId());
        assertThrows(IllegalStateException.class, () -> item.setSupplierReturnId(20L));

        item.setInventoryTransactionId(500L);
        assertEquals(500L, item.getInventoryTransactionId());
        assertThrows(IllegalStateException.class, () -> item.setInventoryTransactionId(600L));
    }

    @Test
    @DisplayName("Should verify equals, hashCode, and toString")
    void shouldVerifyEqualsHashCodeToString() {
        SupplierReturnItem item1 = new SupplierReturnItem(1L, 10L, 100L, 50L, 5, "Defective", 500L);
        SupplierReturnItem item2 = new SupplierReturnItem(1L, 10L, 100L, 50L, 5, "Defective", 500L);
        SupplierReturnItem item3 = new SupplierReturnItem(2L, 10L, 100L, 50L, 5, "Defective", 500L);

        assertEquals(item1, item2);
        assertEquals(item1.hashCode(), item2.hashCode());
        assertNotEquals(item1, item3);
        assertTrue(item1.toString().contains("Defective"));
    }
}
