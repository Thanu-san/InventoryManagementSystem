package com.stockflow.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SupplierReturn Model Tests")
class SupplierReturnTest {

    @Test
    @DisplayName("Should create valid supplier return with line items")
    void shouldCreateValidSupplierReturn() {
        LocalDateTime now = LocalDateTime.now();
        SupplierReturnItem item1 = new SupplierReturnItem(1L, 10L, 100L, 50L, 4, "Damaged", 501L);
        SupplierReturnItem item2 = new SupplierReturnItem(2L, 10L, 101L, 51L, 6, "Incorrect", 502L);

        SupplierReturn ret = new SupplierReturn(10L, "RET-20261020-01", 100L, now, "Supplier RMA-42", List.of(item1, item2));

        assertEquals(10L, ret.getId());
        assertEquals("RET-20261020-01", ret.getReturnNumber());
        assertEquals(100L, ret.getPurchaseOrderId());
        assertEquals(now, ret.getReturnDate());
        assertEquals("Supplier RMA-42", ret.getReason());
        assertEquals(2, ret.getItems().size());
        assertEquals(10, ret.getTotalReturnedUnits());
    }

    @Test
    @DisplayName("Should reject blank return number and invalid PO ID")
    void shouldRejectInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () ->
                new SupplierReturn(null, " ", 10L, LocalDateTime.now(), null, null));
        assertThrows(IllegalArgumentException.class, () ->
                new SupplierReturn(null, "RET-01", 0L, LocalDateTime.now(), null, null));
        assertThrows(IllegalArgumentException.class, () ->
                new SupplierReturn(null, "RET-01", -5L, LocalDateTime.now(), null, null));
    }

    @Test
    @DisplayName("Should allow ID assignment only once")
    void shouldEnforceSingleIdAssignment() {
        SupplierReturn ret = new SupplierReturn("RET-NEW", 10L, "Reason");
        assertNull(ret.getId());

        ret.setId(1L);
        assertEquals(1L, ret.getId());
        assertThrows(IllegalStateException.class, () -> ret.setId(2L));
    }

    @Test
    @DisplayName("Should protect items collection defensively")
    void shouldProtectItemsDefensively() {
        SupplierReturn ret = new SupplierReturn("RET-01", 10L, null);
        SupplierReturnItem item = new SupplierReturnItem(100L, 50L, 2, "Reason");
        ret.addItem(item);

        assertEquals(1, ret.getItems().size());
        assertThrows(UnsupportedOperationException.class, () ->
                ret.getItems().add(new SupplierReturnItem(101L, 51L, 3, "Other")));
    }

    @Test
    @DisplayName("Should find item by item ID or receipt item ID")
    void shouldFindItems() {
        SupplierReturn ret = new SupplierReturn("RET-01", 10L, null);
        SupplierReturnItem item = new SupplierReturnItem(5L, null, 100L, 50L, 3, "Reason", 500L);
        ret.addItem(item);

        assertTrue(ret.findItemById(5L).isPresent());
        assertTrue(ret.findItemByReceiptItemId(100L).isPresent());
        assertFalse(ret.findItemById(99L).isPresent());
        assertFalse(ret.findItemByReceiptItemId(999L).isPresent());
    }

    @Test
    @DisplayName("Should verify equals, hashCode, and toString")
    void shouldVerifyEqualsHashCodeToString() {
        SupplierReturn r1 = new SupplierReturn(1L, "RET-01", 10L, LocalDateTime.now(), "R1", null);
        SupplierReturn r2 = new SupplierReturn(1L, "RET-01", 10L, LocalDateTime.now(), "R1", null);
        SupplierReturn r3 = new SupplierReturn(2L, "RET-02", 10L, LocalDateTime.now(), "R1", null);

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
        assertNotEquals(r1, r3);
        assertTrue(r1.toString().contains("RET-01"));
    }
}
