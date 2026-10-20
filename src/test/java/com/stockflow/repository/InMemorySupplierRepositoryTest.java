package com.stockflow.repository;

import com.stockflow.model.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InMemorySupplierRepository Tests")
class InMemorySupplierRepositoryTest {

    private InMemorySupplierRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemorySupplierRepository();
    }

    // --- Save ---

    @Test
    @DisplayName("Should save supplier and assign generated ID")
    void shouldSaveSupplierAndAssignGeneratedId() {
        Supplier supplier = new Supplier("SUP-001", "Acme Corp", "John", "555", "john@acme.com", "123 Main");

        Supplier saved = repository.save(supplier);

        assertSame(supplier, saved);
        assertNotNull(saved.getId());
        assertTrue(saved.getId() > 0);
        assertEquals("SUP-001", saved.getSupplierCode());
    }

    @Test
    @DisplayName("Should reject save when supplier is null")
    void shouldRejectSaveWhenSupplierIsNull() {
        assertThrows(IllegalArgumentException.class, () -> repository.save(null));
    }

    @Test
    @DisplayName("Should reject save when supplier already has an ID")
    void shouldRejectSaveWhenSupplierAlreadyHasId() {
        Supplier supplier = new Supplier(1L, "SUP-001", "Acme", null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> repository.save(supplier));
    }

    @Test
    @DisplayName("Should reject save when supplier code already exists")
    void shouldRejectSaveWhenCodeAlreadyExists() {
        repository.save(new Supplier("SUP-DUP", "First", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () ->
                repository.save(new Supplier("SUP-DUP", "Second", null, null, null, null)));
    }

    @Test
    @DisplayName("Should assign sequential IDs")
    void shouldAssignSequentialIds() {
        Supplier s1 = repository.save(new Supplier("SUP-A", "Alpha", null, null, null, null));
        Supplier s2 = repository.save(new Supplier("SUP-B", "Beta", null, null, null, null));

        assertTrue(s2.getId() > s1.getId());
    }

    // --- Find ---

    @Test
    @DisplayName("Should find supplier by code")
    void shouldFindSupplierByCode() {
        repository.save(new Supplier("SUP-FIND", "Find Me", "Contact", "555", "a@b.com", "Addr"));

        Optional<Supplier> result = repository.findBySupplierCode("SUP-FIND");

        assertTrue(result.isPresent());
        assertEquals("SUP-FIND", result.get().getSupplierCode());
        assertEquals("Find Me", result.get().getName());
    }

    @Test
    @DisplayName("Should return empty when supplier code not found")
    void shouldReturnEmptyWhenCodeNotFound() {
        assertTrue(repository.findBySupplierCode("NON-EXIST").isEmpty());
    }

    @Test
    @DisplayName("Should return empty when finding by null code")
    void shouldReturnEmptyWhenFindingByNullCode() {
        assertTrue(repository.findBySupplierCode(null).isEmpty());
    }

    @Test
    @DisplayName("Should find supplier by ID")
    void shouldFindSupplierById() {
        Supplier saved = repository.save(new Supplier("SUP-ID-FIND", "Find By ID", null, null, null, null));
        Optional<Supplier> found = repository.findById(saved.getId());

        assertTrue(found.isPresent());
        assertEquals("SUP-ID-FIND", found.get().getSupplierCode());
    }

    @Test
    @DisplayName("Should return empty when finding by nonexistent ID or null")
    void shouldReturnEmptyWhenFindingByNonexistentIdOrNull() {
        assertTrue(repository.findById(99999L).isEmpty());
        assertTrue(repository.findById(null).isEmpty());
    }

    // --- Exists ---

    @Test
    @DisplayName("Should return true when supplier code exists")
    void shouldReturnTrueWhenCodeExists() {
        repository.save(new Supplier("SUP-EX", "Exists", null, null, null, null));
        assertTrue(repository.existsBySupplierCode("SUP-EX"));
    }

    @Test
    @DisplayName("Should return false when supplier code does not exist")
    void shouldReturnFalseWhenCodeDoesNotExist() {
        assertFalse(repository.existsBySupplierCode("NON-EXIST"));
    }

    @Test
    @DisplayName("Should return false when checking null code")
    void shouldReturnFalseWhenCheckingNullCode() {
        assertFalse(repository.existsBySupplierCode(null));
    }

    // --- Find All ---

    @Test
    @DisplayName("Should return all saved suppliers")
    void shouldReturnAllSavedSuppliers() {
        repository.save(new Supplier("SUP-1", "One", null, null, null, null));
        repository.save(new Supplier("SUP-2", "Two", null, null, null, null));

        List<Supplier> all = repository.findAll();
        assertEquals(2, all.size());
    }

    @Test
    @DisplayName("Should return empty list when no suppliers saved")
    void shouldReturnEmptyListWhenEmpty() {
        List<Supplier> all = repository.findAll();
        assertNotNull(all);
        assertTrue(all.isEmpty());
    }

    // --- Update ---

    @Test
    @DisplayName("Should update existing supplier fields")
    void shouldUpdateExistingSupplierFields() {
        repository.save(new Supplier("SUP-UPD", "Old Name", "Old Contact",
                "111", "old@test.com", "Old Addr"));

        Supplier updated = new Supplier("SUP-UPD", "New Name", "New Contact",
                "222", "new@test.com", "New Addr");
        boolean result = repository.update(updated);

        assertTrue(result);
        Supplier stored = repository.findBySupplierCode("SUP-UPD").orElseThrow();
        assertEquals("New Name", stored.getName());
        assertEquals("New Contact", stored.getContactName());
        assertEquals("222", stored.getPhone());
        assertEquals("new@test.com", stored.getEmail());
        assertEquals("New Addr", stored.getAddress());
    }

    @Test
    @DisplayName("Should return false when updating non-existent supplier")
    void shouldReturnFalseWhenUpdatingNonExistent() {
        Supplier supplier = new Supplier("NON-EXIST", "Name", null, null, null, null);
        assertFalse(repository.update(supplier));
    }

    @Test
    @DisplayName("Should return false when updating null supplier")
    void shouldReturnFalseWhenUpdatingNull() {
        assertFalse(repository.update(null));
    }

    // --- Delete ---

    @Test
    @DisplayName("Should delete existing supplier by code")
    void shouldDeleteExistingSupplierByCode() {
        repository.save(new Supplier("SUP-DEL", "Delete Me", null, null, null, null));

        boolean deleted = repository.deleteBySupplierCode("SUP-DEL");

        assertTrue(deleted);
        assertTrue(repository.findBySupplierCode("SUP-DEL").isEmpty());
    }

    @Test
    @DisplayName("Should return false when deleting non-existent supplier")
    void shouldReturnFalseWhenDeletingNonExistent() {
        assertFalse(repository.deleteBySupplierCode("NON-EXIST"));
    }

    @Test
    @DisplayName("Should return false when deleting by null code")
    void shouldReturnFalseWhenDeletingByNullCode() {
        assertFalse(repository.deleteBySupplierCode(null));
    }

    @Test
    @DisplayName("Should not affect other suppliers when one is deleted")
    void shouldNotAffectOtherSuppliersWhenOneDeleted() {
        repository.save(new Supplier("SUP-KEEP", "Keeper", null, null, null, null));
        repository.save(new Supplier("SUP-GONE", "Goner", null, null, null, null));

        repository.deleteBySupplierCode("SUP-GONE");

        assertTrue(repository.findBySupplierCode("SUP-KEEP").isPresent());
        assertTrue(repository.findBySupplierCode("SUP-GONE").isEmpty());
        assertEquals(1, repository.findAll().size());
    }
}
