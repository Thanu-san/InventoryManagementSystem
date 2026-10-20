package com.stockflow.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PurchaseOrderStatus Enum Tests")
class PurchaseOrderStatusTest {

    @Test
    @DisplayName("Should contain DRAFT, PLACED, and CANCELLED statuses")
    void shouldContainExpectedStatuses() {
        assertEquals(3, PurchaseOrderStatus.values().length);
        assertEquals(PurchaseOrderStatus.DRAFT, PurchaseOrderStatus.valueOf("DRAFT"));
        assertEquals(PurchaseOrderStatus.PLACED, PurchaseOrderStatus.valueOf("PLACED"));
        assertEquals(PurchaseOrderStatus.CANCELLED, PurchaseOrderStatus.valueOf("CANCELLED"));
    }
}
