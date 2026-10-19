package com.stockflow.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Supplier Model Tests")
class SupplierTest {

    @Test
    @DisplayName("Should create supplier with all fields successfully")
    void shouldCreateSupplierWithAllFields() {
        Supplier supplier = new Supplier("SUP-001", "Acme Corp", "John Doe",
                "555-1234", "john@acme.com", "123 Main St");

        assertNull(supplier.getId());
        assertEquals("SUP-001", supplier.getSupplierCode());
        assertEquals("Acme Corp", supplier.getName());
        assertEquals("John Doe", supplier.getContactName());
        assertEquals("555-1234", supplier.getPhone());
        assertEquals("john@acme.com", supplier.getEmail());
        assertEquals("123 Main St", supplier.getAddress());
    }

    @Test
    @DisplayName("Should create supplier with null optional fields")
    void shouldCreateSupplierWithNullOptionalFields() {
        Supplier supplier = new Supplier("SUP-002", "Beta Inc", null, null, null, null);

        assertEquals("SUP-002", supplier.getSupplierCode());
        assertEquals("Beta Inc", supplier.getName());
        assertNull(supplier.getContactName());
        assertNull(supplier.getPhone());
        assertNull(supplier.getEmail());
        assertNull(supplier.getAddress());
    }

    @Test
    @DisplayName("Should normalize blank optional fields to null")
    void shouldNormalizeBlankOptionalFieldsToNull() {
        Supplier supplier = new Supplier("SUP-003", "Gamma LLC", "   ", "", "  ", "  ");

        assertNull(supplier.getContactName());
        assertNull(supplier.getPhone());
        assertNull(supplier.getEmail());
        assertNull(supplier.getAddress());
    }

    @Test
    @DisplayName("Should trim supplier code and name")
    void shouldTrimSupplierCodeAndName() {
        Supplier supplier = new Supplier("  SUP-004  ", "  Delta Co  ", null, null, null, null);

        assertEquals("SUP-004", supplier.getSupplierCode());
        assertEquals("Delta Co", supplier.getName());
    }

    @Test
    @DisplayName("Should throw when supplier code is null")
    void shouldThrowWhenSupplierCodeIsNull() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new Supplier(null, "Name", null, null, null, null));
        assertTrue(ex.getMessage().contains("Supplier code cannot be blank"));
    }

    @Test
    @DisplayName("Should throw when supplier code is blank")
    void shouldThrowWhenSupplierCodeIsBlank() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new Supplier("   ", "Name", null, null, null, null));
        assertTrue(ex.getMessage().contains("Supplier code cannot be blank"));
    }

    @Test
    @DisplayName("Should throw when name is null")
    void shouldThrowWhenNameIsNull() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new Supplier("SUP-X", null, null, null, null, null));
        assertTrue(ex.getMessage().contains("Supplier name cannot be blank"));
    }

    @Test
    @DisplayName("Should throw when name is blank")
    void shouldThrowWhenNameIsBlank() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new Supplier("SUP-X", "   ", null, null, null, null));
        assertTrue(ex.getMessage().contains("Supplier name cannot be blank"));
    }

    @Test
    @DisplayName("Should create supplier with full constructor including ID")
    void shouldCreateSupplierWithFullConstructor() {
        Supplier supplier = new Supplier(10L, "SUP-FULL", "Full Supplier",
                "Contact", "111-222", "full@test.com", "456 Elm St");

        assertEquals(10L, supplier.getId());
        assertEquals("SUP-FULL", supplier.getSupplierCode());
        assertEquals("Full Supplier", supplier.getName());
    }

    @Test
    @DisplayName("Should assign ID only once")
    void shouldAssignIdOnlyOnce() {
        Supplier supplier = new Supplier("SUP-ID", "ID Test", null, null, null, null);
        supplier.setId(1L);

        assertThrows(IllegalStateException.class, () -> supplier.setId(2L));
    }

    @Test
    @DisplayName("Should reject null or non-positive ID")
    void shouldRejectInvalidId() {
        Supplier supplier = new Supplier("SUP-ID2", "ID Test 2", null, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> supplier.setId(null));
        assertThrows(IllegalArgumentException.class, () -> supplier.setId(0L));
        assertThrows(IllegalArgumentException.class, () -> supplier.setId(-1L));
    }

    @Test
    @DisplayName("Should update name via setter")
    void shouldUpdateNameViaSetter() {
        Supplier supplier = new Supplier("SUP-SET", "Old Name", null, null, null, null);
        supplier.setName("New Name");
        assertEquals("New Name", supplier.getName());
    }

    @Test
    @DisplayName("Should reject blank name via setter")
    void shouldRejectBlankNameViaSetter() {
        Supplier supplier = new Supplier("SUP-SET2", "Name", null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> supplier.setName("   "));
        assertThrows(IllegalArgumentException.class, () -> supplier.setName(null));
    }

    @Test
    @DisplayName("Should update optional fields via setters")
    void shouldUpdateOptionalFieldsViaSetters() {
        Supplier supplier = new Supplier("SUP-OPT", "Optional Test", null, null, null, null);

        supplier.setContactName("New Contact");
        assertEquals("New Contact", supplier.getContactName());

        supplier.setPhone("555-0000");
        assertEquals("555-0000", supplier.getPhone());

        supplier.setEmail("new@test.com");
        assertEquals("new@test.com", supplier.getEmail());

        supplier.setAddress("789 Oak Ave");
        assertEquals("789 Oak Ave", supplier.getAddress());
    }

    @Test
    @DisplayName("Should normalize blank optional setters to null")
    void shouldNormalizeBlankOptionalSettersToNull() {
        Supplier supplier = new Supplier("SUP-NORM", "Norm Test", "Contact",
                "555", "a@b.com", "Addr");

        supplier.setContactName("   ");
        assertNull(supplier.getContactName());

        supplier.setPhone("");
        assertNull(supplier.getPhone());

        supplier.setEmail(null);
        assertNull(supplier.getEmail());

        supplier.setAddress("  ");
        assertNull(supplier.getAddress());
    }

    @Test
    @DisplayName("Should produce readable toString output")
    void shouldProduceReadableToString() {
        Supplier supplier = new Supplier("SUP-STR", "String Test", null, null, null, null);
        String str = supplier.toString();
        assertTrue(str.contains("SUP-STR"));
        assertTrue(str.contains("String Test"));
    }
}
