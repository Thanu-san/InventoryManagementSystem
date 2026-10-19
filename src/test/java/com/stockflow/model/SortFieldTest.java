package com.stockflow.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SortField Enum Tests")
class SortFieldTest {

    @Test
    @DisplayName("Should parse valid sort field strings case-insensitively")
    void shouldParseValidSortFieldStrings() {
        assertEquals(SortField.SKU, SortField.fromString("sku"));
        assertEquals(SortField.SKU, SortField.fromString("SKU"));
        assertEquals(SortField.NAME, SortField.fromString("name"));
        assertEquals(SortField.NAME, SortField.fromString("NAME"));
        assertEquals(SortField.PRICE, SortField.fromString("price"));
        assertEquals(SortField.PRICE, SortField.fromString("PRICE"));
        assertEquals(SortField.QUANTITY, SortField.fromString("quantity"));
        assertEquals(SortField.QUANTITY, SortField.fromString("QUANTITY"));
        assertEquals(SortField.SKU, SortField.fromString("  sku  "));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when parsing null, blank, or unsupported sort field")
    void shouldFailOnInvalidSortField() {
        assertThrows(IllegalArgumentException.class, () -> SortField.fromString(null));
        assertThrows(IllegalArgumentException.class, () -> SortField.fromString(""));
        assertThrows(IllegalArgumentException.class, () -> SortField.fromString("   "));
        assertThrows(IllegalArgumentException.class, () -> SortField.fromString("unknown"));
        assertThrows(IllegalArgumentException.class, () -> SortField.fromString("id"));
    }
}
