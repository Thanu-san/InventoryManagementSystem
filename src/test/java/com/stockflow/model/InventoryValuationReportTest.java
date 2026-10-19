package com.stockflow.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InventoryValuationReport Model Tests")
class InventoryValuationReportTest {

    @Test
    @DisplayName("Should create InventoryValuationReport with valid inputs")
    void shouldCreateValuationReportWithValidInputs() {
        InventoryValuationItem item = new InventoryValuationItem(
                "KB-001",
                "Keyboard",
                new BigDecimal("25.00"),
                10,
                new BigDecimal("250.00")
        );
        InventoryValuationReport report = new InventoryValuationReport(
                List.of(item),
                10L,
                new BigDecimal("250.00")
        );

        assertEquals(1, report.getItems().size());
        assertEquals(item, report.getItems().get(0));
        assertEquals(10L, report.getTotalQuantity());
        assertEquals(new BigDecimal("250.00"), report.getTotalValue());
    }

    @Test
    @DisplayName("Should create empty InventoryValuationReport with zero units and 0.00 total value")
    void shouldCreateEmptyValuationReport() {
        InventoryValuationReport report = InventoryValuationReport.empty();

        assertNotNull(report);
        assertTrue(report.getItems().isEmpty());
        assertEquals(0L, report.getTotalQuantity());
        assertEquals(new BigDecimal("0.00"), report.getTotalValue());
    }

    @Test
    @DisplayName("Should reject null items list in constructor")
    void shouldRejectNullItemsList() {
        assertThrows(IllegalArgumentException.class, () ->
                new InventoryValuationReport(null, 0L, BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Should reject negative total quantity in constructor")
    void shouldRejectNegativeTotalQuantity() {
        assertThrows(IllegalArgumentException.class, () ->
                new InventoryValuationReport(List.of(), -1L, BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Should reject null or negative total value in constructor")
    void shouldRejectNullOrNegativeTotalValue() {
        assertThrows(IllegalArgumentException.class, () ->
                new InventoryValuationReport(List.of(), 0L, null));
        assertThrows(IllegalArgumentException.class, () ->
                new InventoryValuationReport(List.of(), 0L, new BigDecimal("-0.01")));
    }

    @Test
    @DisplayName("Should maintain immutability of items list")
    void shouldMaintainImmutabilityOfItems() {
        List<InventoryValuationItem> mutableList = new ArrayList<>();
        mutableList.add(new InventoryValuationItem("KB-001", "Keyboard", new BigDecimal("25.00"), 10, new BigDecimal("250.00")));

        InventoryValuationReport report = new InventoryValuationReport(mutableList, 10L, new BigDecimal("250.00"));

        assertThrows(UnsupportedOperationException.class, () ->
                report.getItems().add(new InventoryValuationItem("MS-001", "Mouse", new BigDecimal("10.00"), 5, new BigDecimal("50.00"))));

        // Mutating original list should not affect the report
        mutableList.clear();
        assertEquals(1, report.getItems().size());
    }

    @Test
    @DisplayName("Should implement equals and hashCode correctly")
    void shouldImplementEqualsAndHashCode() {
        InventoryValuationItem item = new InventoryValuationItem("KB-001", "Keyboard", new BigDecimal("25.00"), 10, new BigDecimal("250.00"));
        InventoryValuationReport report1 = new InventoryValuationReport(List.of(item), 10L, new BigDecimal("250.00"));
        InventoryValuationReport report2 = new InventoryValuationReport(List.of(item), 10L, new BigDecimal("250.00"));
        InventoryValuationReport report3 = InventoryValuationReport.empty();

        assertEquals(report1, report2);
        assertEquals(report1.hashCode(), report2.hashCode());
        assertNotEquals(report1, report3);
        assertNotEquals(report1, null);
        assertNotEquals(report1, "other");
    }

    @Test
    @DisplayName("Should generate meaningful toString output")
    void shouldGenerateMeaningfulToString() {
        InventoryValuationReport report = new InventoryValuationReport(List.of(), 0L, new BigDecimal("0.00"));
        String str = report.toString();

        assertTrue(str.contains("items="));
        assertTrue(str.contains("totalQuantity=0"));
        assertTrue(str.contains("totalValue=0.00"));
    }
}
