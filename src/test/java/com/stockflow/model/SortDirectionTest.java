package com.stockflow.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SortDirection Enum Tests")
class SortDirectionTest {

    @Test
    @DisplayName("Should parse valid sort direction strings case-insensitively")
    void shouldParseValidSortDirectionStrings() {
        assertEquals(SortDirection.ASC, SortDirection.fromString("asc"));
        assertEquals(SortDirection.ASC, SortDirection.fromString("ASC"));
        assertEquals(SortDirection.ASC, SortDirection.fromString("ascending"));
        assertEquals(SortDirection.ASC, SortDirection.fromString("ASCENDING"));

        assertEquals(SortDirection.DESC, SortDirection.fromString("desc"));
        assertEquals(SortDirection.DESC, SortDirection.fromString("DESC"));
        assertEquals(SortDirection.DESC, SortDirection.fromString("descending"));
        assertEquals(SortDirection.DESC, SortDirection.fromString("DESCENDING"));

        assertEquals(SortDirection.ASC, SortDirection.fromString("  asc  "));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when parsing null, blank, or unsupported sort direction")
    void shouldFailOnInvalidSortDirection() {
        assertThrows(IllegalArgumentException.class, () -> SortDirection.fromString(null));
        assertThrows(IllegalArgumentException.class, () -> SortDirection.fromString(""));
        assertThrows(IllegalArgumentException.class, () -> SortDirection.fromString("   "));
        assertThrows(IllegalArgumentException.class, () -> SortDirection.fromString("sideways"));
    }
}
