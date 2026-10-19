package com.stockflow.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InventoryValuationItem Model Tests")
class InventoryValuationItemTest {

    @Test
    @DisplayName("Should create InventoryValuationItem with valid inputs")
    void shouldCreateValuationItemWithValidInputs() {
        InventoryValuationItem item = new InventoryValuationItem(
                "KB-001",
                "Keyboard",
                new BigDecimal("25.00"),
                10,
                new BigDecimal("250.00")
        );

        assertEquals("KB-001", item.getSku());
        assertEquals("Keyboard", item.getName());
        assertEquals(new BigDecimal("25.00"), item.getUnitPrice());
        assertEquals(10, item.getQuantity());
        assertEquals(new BigDecimal("250.00"), item.getInventoryValue());
    }

    @Test
    @DisplayName("Should reject blank or null SKU")
    void shouldRejectBlankOrNullSku() {
        assertThrows(IllegalArgumentException.class, () ->
                new InventoryValuationItem(null, "Keyboard", new BigDecimal("25.00"), 10, new BigDecimal("250.00")));
        assertThrows(IllegalArgumentException.class, () ->
                new InventoryValuationItem("   ", "Keyboard", new BigDecimal("25.00"), 10, new BigDecimal("250.00")));
    }

    @Test
    @DisplayName("Should reject blank or null product name")
    void shouldRejectBlankOrNullName() {
        assertThrows(IllegalArgumentException.class, () ->
                new InventoryValuationItem("KB-001", null, new BigDecimal("25.00"), 10, new BigDecimal("250.00")));
        assertThrows(IllegalArgumentException.class, () ->
                new InventoryValuationItem("KB-001", "  ", new BigDecimal("25.00"), 10, new BigDecimal("250.00")));
    }

    @Test
    @DisplayName("Should reject null or negative unit price")
    void shouldRejectNullOrNegativeUnitPrice() {
        assertThrows(IllegalArgumentException.class, () ->
                new InventoryValuationItem("KB-001", "Keyboard", null, 10, new BigDecimal("250.00")));
        assertThrows(IllegalArgumentException.class, () ->
                new InventoryValuationItem("KB-001", "Keyboard", new BigDecimal("-0.01"), 10, new BigDecimal("250.00")));
    }

    @Test
    @DisplayName("Should reject negative quantity")
    void shouldRejectNegativeQuantity() {
        assertThrows(IllegalArgumentException.class, () ->
                new InventoryValuationItem("KB-001", "Keyboard", new BigDecimal("25.00"), -1, new BigDecimal("250.00")));
    }

    @Test
    @DisplayName("Should reject null or negative inventory value")
    void shouldRejectNullOrNegativeInventoryValue() {
        assertThrows(IllegalArgumentException.class, () ->
                new InventoryValuationItem("KB-001", "Keyboard", new BigDecimal("25.00"), 10, null));
        assertThrows(IllegalArgumentException.class, () ->
                new InventoryValuationItem("KB-001", "Keyboard", new BigDecimal("25.00"), 10, new BigDecimal("-1.00")));
    }

    @Test
    @DisplayName("Should implement equals and hashCode correctly")
    void shouldImplementEqualsAndHashCode() {
        InventoryValuationItem item1 = new InventoryValuationItem("KB-001", "Keyboard", new BigDecimal("25.00"), 10, new BigDecimal("250.00"));
        InventoryValuationItem item2 = new InventoryValuationItem("KB-001", "Keyboard", new BigDecimal("25.00"), 10, new BigDecimal("250.00"));
        InventoryValuationItem item3 = new InventoryValuationItem("MS-001", "Mouse", new BigDecimal("12.50"), 8, new BigDecimal("100.00"));

        assertEquals(item1, item2);
        assertEquals(item1.hashCode(), item2.hashCode());
        assertNotEquals(item1, item3);
        assertNotEquals(item1, null);
        assertNotEquals(item1, "other");
    }

    @Test
    @DisplayName("Should generate meaningful toString output")
    void shouldGenerateMeaningfulToString() {
        InventoryValuationItem item = new InventoryValuationItem("KB-001", "Keyboard", new BigDecimal("25.00"), 10, new BigDecimal("250.00"));
        String str = item.toString();

        assertTrue(str.contains("sku='KB-001'"));
        assertTrue(str.contains("name='Keyboard'"));
        assertTrue(str.contains("unitPrice=25.00"));
        assertTrue(str.contains("quantity=10"));
        assertTrue(str.contains("inventoryValue=250.00"));
    }
}
