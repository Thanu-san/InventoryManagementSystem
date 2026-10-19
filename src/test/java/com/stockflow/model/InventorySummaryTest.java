package com.stockflow.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InventorySummary Model Tests")
class InventorySummaryTest {

    @Test
    @DisplayName("Should create InventorySummary with valid metrics")
    void shouldCreateInventorySummaryWithValidMetrics() {
        InventorySummary summary = new InventorySummary(10, 150, 3, 1);

        assertEquals(10, summary.getTotalProducts());
        assertEquals(150, summary.getTotalUnits());
        assertEquals(3, summary.getLowStockProducts());
        assertEquals(1, summary.getOutOfStockProducts());
    }

    @Test
    @DisplayName("Should create InventorySummary with all zeroes for empty inventory")
    void shouldCreateInventorySummaryWithZeroes() {
        InventorySummary summary = new InventorySummary(0, 0, 0, 0);

        assertEquals(0, summary.getTotalProducts());
        assertEquals(0, summary.getTotalUnits());
        assertEquals(0, summary.getLowStockProducts());
        assertEquals(0, summary.getOutOfStockProducts());
    }

    @Test
    @DisplayName("Should reject negative values in constructor")
    void shouldRejectNegativeValues() {
        assertThrows(IllegalArgumentException.class, () -> new InventorySummary(-1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new InventorySummary(0, -1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new InventorySummary(0, 0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new InventorySummary(0, 0, 0, -1));
    }

    @Test
    @DisplayName("Should implement equals and hashCode correctly")
    void shouldImplementEqualsAndHashCode() {
        InventorySummary s1 = new InventorySummary(5, 50, 2, 1);
        InventorySummary s2 = new InventorySummary(5, 50, 2, 1);
        InventorySummary s3 = new InventorySummary(6, 50, 2, 1);

        assertEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());
        assertNotEquals(s1, s3);
        assertNotEquals(s1, null);
        assertNotEquals(s1, "other");
    }

    @Test
    @DisplayName("Should generate meaningful toString output")
    void shouldGenerateToString() {
        InventorySummary summary = new InventorySummary(5, 100, 2, 1);
        String str = summary.toString();

        assertTrue(str.contains("totalProducts=5"));
        assertTrue(str.contains("totalUnits=100"));
        assertTrue(str.contains("lowStockProducts=2"));
        assertTrue(str.contains("outOfStockProducts=1"));
    }
}
