package com.stockflow.service;

import com.stockflow.exception.ValidationException;
import com.stockflow.model.Supplier;
import com.stockflow.repository.InMemorySupplierRepository;
import com.stockflow.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SupplierService Business Rule Tests")
class SupplierServiceTest {

    private SupplierRepository supplierRepository;
    private SupplierService supplierService;

    @BeforeEach
    void setUp() {
        supplierRepository = new InMemorySupplierRepository();
        supplierService = new SupplierService(supplierRepository);
    }

    // --- Constructor ---

    @Test
    @DisplayName("Should reject null repository")
    void shouldRejectNullRepository() {
        assertThrows(IllegalArgumentException.class, () -> new SupplierService(null));
    }

    // --- Create ---

    @Test
    @DisplayName("Should create supplier successfully with all fields")
    void shouldCreateSupplierSuccessfully() {
        Supplier supplier = supplierService.createSupplier(
                "SUP-001", "Acme Corp", "John Doe", "555-1234", "john@acme.com", "123 Main St");

        assertNotNull(supplier);
        assertNotNull(supplier.getId());
        assertEquals("SUP-001", supplier.getSupplierCode());
        assertEquals("Acme Corp", supplier.getName());
        assertEquals("John Doe", supplier.getContactName());
        assertEquals("555-1234", supplier.getPhone());
        assertEquals("john@acme.com", supplier.getEmail());
        assertEquals("123 Main St", supplier.getAddress());

        List<Supplier> all = supplierService.getAllSuppliers();
        assertEquals(1, all.size());
    }

    @Test
    @DisplayName("Should create supplier with null optional fields")
    void shouldCreateSupplierWithNullOptionalFields() {
        Supplier supplier = supplierService.createSupplier("SUP-002", "Beta Inc", null, null, null, null);

        assertNotNull(supplier.getId());
        assertEquals("Beta Inc", supplier.getName());
        assertNull(supplier.getContactName());
        assertNull(supplier.getPhone());
        assertNull(supplier.getEmail());
        assertNull(supplier.getAddress());
    }

    @Test
    @DisplayName("Should create supplier with blank optional fields normalized to null")
    void shouldCreateSupplierWithBlankOptionalFieldsNormalized() {
        Supplier supplier = supplierService.createSupplier("SUP-003", "Gamma LLC", "   ", "", "  ", "  ");

        assertNull(supplier.getContactName());
        assertNull(supplier.getPhone());
        assertNull(supplier.getEmail());
        assertNull(supplier.getAddress());
    }

    @Test
    @DisplayName("Should throw ValidationException when supplier code is blank")
    void shouldFailWhenSupplierCodeIsBlank() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                supplierService.createSupplier("   ", "Name", null, null, null, null));
        assertTrue(ex.getMessage().contains("Supplier code cannot be blank"));
    }

    @Test
    @DisplayName("Should throw ValidationException when supplier code is null")
    void shouldFailWhenSupplierCodeIsNull() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                supplierService.createSupplier(null, "Name", null, null, null, null));
        assertTrue(ex.getMessage().contains("Supplier code cannot be blank"));
    }

    @Test
    @DisplayName("Should throw ValidationException when name is blank")
    void shouldFailWhenNameIsBlank() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                supplierService.createSupplier("SUP-X", "   ", null, null, null, null));
        assertTrue(ex.getMessage().contains("Supplier name cannot be blank"));
    }

    @Test
    @DisplayName("Should throw ValidationException when name is null")
    void shouldFailWhenNameIsNull() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                supplierService.createSupplier("SUP-X", null, null, null, null, null));
        assertTrue(ex.getMessage().contains("Supplier name cannot be blank"));
    }

    @Test
    @DisplayName("Should throw ValidationException when supplier code is duplicate")
    void shouldFailWhenSupplierCodeIsDuplicate() {
        supplierService.createSupplier("SUP-DUP", "First", null, null, null, null);

        ValidationException ex = assertThrows(ValidationException.class, () ->
                supplierService.createSupplier("SUP-DUP", "Second", null, null, null, null));
        assertTrue(ex.getMessage().contains("already exists"));
    }

    @Test
    @DisplayName("Should not persist supplier when validation fails")
    void shouldNotPersistSupplierWhenValidationFails() {
        assertThrows(ValidationException.class, () ->
                supplierService.createSupplier("   ", "Name", null, null, null, null));

        assertTrue(supplierService.getAllSuppliers().isEmpty());
    }

    // --- Find ---

    @Test
    @DisplayName("Should find supplier by code when exists")
    void shouldFindSupplierByCodeWhenExists() {
        supplierService.createSupplier("SUP-FIND", "Find Me", "Contact", "555", "a@b.com", "Addr");

        Optional<Supplier> result = supplierService.findSupplierByCode("SUP-FIND");

        assertTrue(result.isPresent());
        assertEquals("SUP-FIND", result.get().getSupplierCode());
        assertEquals("Find Me", result.get().getName());
    }

    @Test
    @DisplayName("Should return empty Optional when supplier does not exist")
    void shouldReturnEmptyWhenSupplierDoesNotExist() {
        Optional<Supplier> result = supplierService.findSupplierByCode("NON-EXISTENT");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should throw ValidationException when finding by blank code")
    void shouldFailWhenFindingByBlankCode() {
        assertThrows(ValidationException.class, () -> supplierService.findSupplierByCode("   "));
        assertThrows(ValidationException.class, () -> supplierService.findSupplierByCode(null));
    }

    @Test
    @DisplayName("Should find supplier by code with surrounding spaces")
    void shouldFindSupplierByCodeWithSurroundingSpaces() {
        supplierService.createSupplier("SUP-TRIM", "Trim Test", null, null, null, null);

        Optional<Supplier> result = supplierService.findSupplierByCode("  SUP-TRIM  ");
        assertTrue(result.isPresent());
    }

    // --- List All ---

    @Test
    @DisplayName("Should return all suppliers")
    void shouldReturnAllSuppliers() {
        supplierService.createSupplier("SUP-A", "Alpha", null, null, null, null);
        supplierService.createSupplier("SUP-B", "Beta", null, null, null, null);

        List<Supplier> all = supplierService.getAllSuppliers();
        assertEquals(2, all.size());
    }

    @Test
    @DisplayName("Should return empty list when no suppliers exist")
    void shouldReturnEmptyListWhenNoSuppliersExist() {
        List<Supplier> all = supplierService.getAllSuppliers();
        assertNotNull(all);
        assertTrue(all.isEmpty());
    }

    // --- Update ---

    @Test
    @DisplayName("Should update existing supplier successfully")
    void shouldUpdateExistingSupplierSuccessfully() {
        supplierService.createSupplier("SUP-UPD", "Old Name", "Old Contact",
                "111", "old@test.com", "Old Addr");

        Supplier updated = supplierService.updateSupplier("SUP-UPD", "New Name",
                "New Contact", "222", "new@test.com", "New Addr");

        assertEquals("New Name", updated.getName());
        assertEquals("New Contact", updated.getContactName());
        assertEquals("222", updated.getPhone());
        assertEquals("new@test.com", updated.getEmail());
        assertEquals("New Addr", updated.getAddress());

        // Verify persisted
        Supplier stored = supplierService.findSupplierByCode("SUP-UPD").orElseThrow();
        assertEquals("New Name", stored.getName());
    }

    @Test
    @DisplayName("Should clear optional fields when updating with null/blank")
    void shouldClearOptionalFieldsOnUpdate() {
        supplierService.createSupplier("SUP-CLR", "Name", "Contact", "555", "a@b.com", "Addr");

        Supplier updated = supplierService.updateSupplier("SUP-CLR", "Name",
                null, "", "  ", null);

        assertNull(updated.getContactName());
        assertNull(updated.getPhone());
        assertNull(updated.getEmail());
        assertNull(updated.getAddress());
    }

    @Test
    @DisplayName("Should preserve ID and code when updating supplier")
    void shouldPreserveIdAndCodeOnUpdate() {
        Supplier original = supplierService.createSupplier("SUP-INV", "Original",
                null, null, null, null);
        Long originalId = original.getId();

        Supplier updated = supplierService.updateSupplier("SUP-INV", "Updated",
                null, null, null, null);

        assertEquals(originalId, updated.getId());
        assertEquals("SUP-INV", updated.getSupplierCode());
    }

    @Test
    @DisplayName("Should throw ValidationException when updating non-existent supplier")
    void shouldFailWhenUpdatingNonExistentSupplier() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                supplierService.updateSupplier("NON-EXIST", "Name", null, null, null, null));
        assertTrue(ex.getMessage().contains("Supplier not found"));
    }

    @Test
    @DisplayName("Should throw ValidationException when updating with blank code")
    void shouldFailWhenUpdatingWithBlankCode() {
        assertThrows(ValidationException.class, () ->
                supplierService.updateSupplier("   ", "Name", null, null, null, null));
    }

    @Test
    @DisplayName("Should throw ValidationException when updating with blank name")
    void shouldFailWhenUpdatingWithBlankName() {
        supplierService.createSupplier("SUP-BN", "Name", null, null, null, null);

        ValidationException ex = assertThrows(ValidationException.class, () ->
                supplierService.updateSupplier("SUP-BN", "   ", null, null, null, null));
        assertTrue(ex.getMessage().contains("Supplier name cannot be blank"));
    }

    @Test
    @DisplayName("Should not mutate supplier data when validation fails during update")
    void shouldNotMutateSupplierWhenValidationFails() {
        supplierService.createSupplier("SUP-SAFE", "Safe Name", "Contact",
                "555", "safe@test.com", "Safe Addr");

        assertThrows(ValidationException.class, () ->
                supplierService.updateSupplier("SUP-SAFE", "   ", null, null, null, null));

        Supplier stored = supplierService.findSupplierByCode("SUP-SAFE").orElseThrow();
        assertEquals("Safe Name", stored.getName());
        assertEquals("Contact", stored.getContactName());
        assertEquals("555", stored.getPhone());
        assertEquals("safe@test.com", stored.getEmail());
        assertEquals("Safe Addr", stored.getAddress());
    }

    // --- Delete ---

    @Test
    @DisplayName("Should delete existing supplier successfully")
    void shouldDeleteExistingSupplierSuccessfully() {
        supplierService.createSupplier("SUP-DEL", "Delete Me", null, null, null, null);

        supplierService.deleteSupplierByCode("SUP-DEL");

        assertTrue(supplierService.findSupplierByCode("SUP-DEL").isEmpty());
        assertTrue(supplierService.getAllSuppliers().isEmpty());
    }

    @Test
    @DisplayName("Should throw ValidationException when deleting non-existent supplier")
    void shouldFailWhenDeletingNonExistentSupplier() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                supplierService.deleteSupplierByCode("NON-EXIST"));
        assertTrue(ex.getMessage().contains("Supplier not found"));
    }

    @Test
    @DisplayName("Should throw ValidationException when deleting with blank code")
    void shouldFailWhenDeletingWithBlankCode() {
        assertThrows(ValidationException.class, () -> supplierService.deleteSupplierByCode("   "));
        assertThrows(ValidationException.class, () -> supplierService.deleteSupplierByCode(null));
    }

    @Test
    @DisplayName("Should not affect other suppliers when one is deleted")
    void shouldNotAffectOtherSuppliersWhenOneIsDeleted() {
        supplierService.createSupplier("SUP-KEEP", "Keeper", null, null, null, null);
        supplierService.createSupplier("SUP-GONE", "Goner", null, null, null, null);

        supplierService.deleteSupplierByCode("SUP-GONE");

        assertTrue(supplierService.findSupplierByCode("SUP-GONE").isEmpty());
        assertTrue(supplierService.findSupplierByCode("SUP-KEEP").isPresent());
        assertEquals(1, supplierService.getAllSuppliers().size());
    }

    @Test
    @DisplayName("Should delete supplier with surrounding spaces in code")
    void shouldDeleteSupplierWithSurroundingSpaces() {
        supplierService.createSupplier("SUP-SPC", "Spacer", null, null, null, null);

        supplierService.deleteSupplierByCode("  SUP-SPC  ");

        assertTrue(supplierService.findSupplierByCode("SUP-SPC").isEmpty());
    }
}
