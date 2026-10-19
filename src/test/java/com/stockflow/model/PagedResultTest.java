package com.stockflow.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PagedResult Model Tests")
class PagedResultTest {

    @Test
    @DisplayName("Should create PagedResult with valid metadata")
    void shouldCreatePagedResultWithValidMetadata() {
        List<String> items = List.of("Item 1", "Item 2");
        PagedResult<String> page = new PagedResult<>(items, 1, 2, 5L);

        assertEquals(2, page.getContent().size());
        assertEquals(1, page.getPageNumber());
        assertEquals(2, page.getPageSize());
        assertEquals(5L, page.getTotalElements());
        assertEquals(3, page.getTotalPages()); // ceil(5/2) = 3
        assertEquals(2, page.getNumberOfElements());
        assertTrue(page.hasNext());
        assertFalse(page.hasPrevious());
        assertTrue(page.isFirst());
        assertFalse(page.isLast());
        assertFalse(page.isEmpty());
    }

    @Test
    @DisplayName("Should create empty PagedResult correctly")
    void shouldCreateEmptyPagedResult() {
        PagedResult<String> empty = PagedResult.empty(1, 10);

        assertTrue(empty.getContent().isEmpty());
        assertEquals(1, empty.getPageNumber());
        assertEquals(10, empty.getPageSize());
        assertEquals(0L, empty.getTotalElements());
        assertEquals(0, empty.getTotalPages());
        assertFalse(empty.hasNext());
        assertFalse(empty.hasPrevious());
        assertTrue(empty.isFirst());
        assertTrue(empty.isLast());
        assertTrue(empty.isEmpty());
    }

    @Test
    @DisplayName("Should calculate totalPages accurately for exact division")
    void shouldCalculateTotalPagesForExactDivision() {
        PagedResult<String> page = new PagedResult<>(List.of("A", "B"), 1, 2, 4L);
        assertEquals(2, page.getTotalPages());
    }

    @Test
    @DisplayName("Should report correct navigation flags for middle and last pages")
    void shouldReportNavigationFlagsCorrectly() {
        // Middle page: page 2 of 3
        PagedResult<String> middle = new PagedResult<>(List.of("C", "D"), 2, 2, 5L);
        assertTrue(middle.hasNext());
        assertTrue(middle.hasPrevious());
        assertFalse(middle.isFirst());
        assertFalse(middle.isLast());

        // Last page: page 3 of 3
        PagedResult<String> last = new PagedResult<>(List.of("E"), 3, 2, 5L);
        assertFalse(last.hasNext());
        assertTrue(last.hasPrevious());
        assertFalse(last.isFirst());
        assertTrue(last.isLast());

        // Beyond last page: page 4 of 3
        PagedResult<String> beyond = new PagedResult<>(List.of(), 4, 2, 5L);
        assertFalse(beyond.hasNext());
        assertTrue(beyond.hasPrevious());
        assertFalse(beyond.isFirst());
        assertTrue(beyond.isLast());
    }

    @Test
    @DisplayName("Should reject invalid constructor parameters")
    void shouldRejectInvalidConstructorParameters() {
        assertThrows(IllegalArgumentException.class, () -> new PagedResult<>(null, 1, 10, 0L));
        assertThrows(IllegalArgumentException.class, () -> new PagedResult<>(List.of(), 0, 10, 0L));
        assertThrows(IllegalArgumentException.class, () -> new PagedResult<>(List.of(), -1, 10, 0L));
        assertThrows(IllegalArgumentException.class, () -> new PagedResult<>(List.of(), 1, 0, 0L));
        assertThrows(IllegalArgumentException.class, () -> new PagedResult<>(List.of(), 1, -5, 0L));
        assertThrows(IllegalArgumentException.class, () -> new PagedResult<>(List.of(), 1, 10, -1L));
    }

    @Test
    @DisplayName("Should enforce immutability on content list")
    void shouldEnforceImmutabilityOnContent() {
        List<String> mutable = new ArrayList<>();
        mutable.add("Initial");

        PagedResult<String> result = new PagedResult<>(mutable, 1, 5, 1L);
        assertThrows(UnsupportedOperationException.class, () -> result.getContent().add("New"));

        mutable.clear();
        assertEquals(1, result.getContent().size());
    }

    @Test
    @DisplayName("Should implement equals and hashCode correctly")
    void shouldImplementEqualsAndHashCode() {
        PagedResult<String> p1 = new PagedResult<>(List.of("A"), 1, 5, 1L);
        PagedResult<String> p2 = new PagedResult<>(List.of("A"), 1, 5, 1L);
        PagedResult<String> p3 = new PagedResult<>(List.of("B"), 1, 5, 1L);

        assertEquals(p1, p2);
        assertEquals(p1.hashCode(), p2.hashCode());
        assertNotEquals(p1, p3);
        assertNotEquals(p1, null);
        assertNotEquals(p1, "other");
    }

    @Test
    @DisplayName("Should generate meaningful toString output")
    void shouldGenerateMeaningfulToString() {
        PagedResult<String> result = new PagedResult<>(List.of("A"), 1, 5, 1L);
        String str = result.toString();

        assertTrue(str.contains("pageNumber=1"));
        assertTrue(str.contains("pageSize=5"));
        assertTrue(str.contains("totalElements=1"));
        assertTrue(str.contains("totalPages=1"));
    }
}
