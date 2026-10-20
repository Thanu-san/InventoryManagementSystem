package com.stockflow.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PurchaseOrderStatus Enum Tests")
class PurchaseOrderStatusTest {

    @Test
    @DisplayName("Should contain all expected lifecycle statuses")
    void shouldContainExpectedStatuses() {
        assertEquals(5, PurchaseOrderStatus.values().length);
        assertEquals(PurchaseOrderStatus.DRAFT, PurchaseOrderStatus.valueOf("DRAFT"));
        assertEquals(PurchaseOrderStatus.PLACED, PurchaseOrderStatus.valueOf("PLACED"));
        assertEquals(PurchaseOrderStatus.PARTIALLY_RECEIVED, PurchaseOrderStatus.valueOf("PARTIALLY_RECEIVED"));
        assertEquals(PurchaseOrderStatus.RECEIVED, PurchaseOrderStatus.valueOf("RECEIVED"));
        assertEquals(PurchaseOrderStatus.CANCELLED, PurchaseOrderStatus.valueOf("CANCELLED"));
    }
}
