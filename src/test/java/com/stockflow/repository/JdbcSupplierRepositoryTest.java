package com.stockflow.repository;

import com.stockflow.database.DatabaseConnection;
import com.stockflow.exception.ValidationException;
import com.stockflow.model.Product;
import com.stockflow.model.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JdbcSupplierRepository Integration Tests")
class JdbcSupplierRepositoryTest {

    private JdbcSupplierRepository repository;
    private String testCode;
    private String testCode2;
    private final List<String> extraTestCodes = new ArrayList<>();
    private final List<String> extraTestSkus = new ArrayList<>();

    @BeforeEach
    void setUp() {
        repository = new JdbcSupplierRepository();
        testCode = "TSUP-" + System.currentTimeMillis();
        testCode2 = "TSUP-2-" + System.currentTimeMillis();
        extraTestCodes.clear();
        extraTestSkus.clear();

        // Ensure the suppliers table and products.supplier_id foreign key exist
        try (Connection connection = DatabaseConnection.getConnection();
             Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS suppliers (
                    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    supplier_code   VARCHAR(50)  NOT NULL UNIQUE,
                    name            VARCHAR(255) NOT NULL,
                    contact_name    VARCHAR(255),
                    phone           VARCHAR(50),
                    email           VARCHAR(255),
                    address         VARCHAR(500)
                );
                ALTER TABLE products 
                ADD COLUMN IF NOT EXISTS supplier_id BIGINT REFERENCES suppliers(id) ON DELETE RESTRICT;
            """);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to ensure suppliers table and relationships exist for tests", e);
        }
    }

    @AfterEach
    void tearDown() {
        try (Connection connection = DatabaseConnection.getConnection()) {
            // Delete any test products first to respect foreign key constraint
            for (String sku : extraTestSkus) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM products WHERE sku = ?")) {
                    statement.setString(1, sku);
                    statement.executeUpdate();
                } catch (SQLException ignored) {
                }
            }
            for (String code : extraTestCodes) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM suppliers WHERE supplier_code = ?")) {
                    statement.setString(1, code);
                    statement.executeUpdate();
                } catch (SQLException ignored) {
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM suppliers WHERE supplier_code = ? OR supplier_code = ?")) {
                statement.setString(1, testCode);
                statement.setString(2, testCode2);
                statement.executeUpdate();
            }
        } catch (SQLException ignored) {
        }
    }

    @Test
    @DisplayName("Should save supplier and assign generated ID")
    void shouldSaveSupplierAndAssignGeneratedId() {
        Supplier supplier = new Supplier(testCode, "Test Supplier", "Contact Person",
                "555-1234", "test@supplier.com", "123 Test St");

        assertNull(supplier.getId());

        Supplier saved = repository.save(supplier);

        assertSame(supplier, saved);
        assertNotNull(saved.getId());
        assertTrue(saved.getId() > 0);
        assertEquals(testCode, saved.getSupplierCode());
        assertEquals("Test Supplier", saved.getName());
        assertEquals("Contact Person", saved.getContactName());
        assertEquals("555-1234", saved.getPhone());
        assertEquals("test@supplier.com", saved.getEmail());
        assertEquals("123 Test St", saved.getAddress());
    }

    @Test
    @DisplayName("Should save supplier with null optional fields")
    void shouldSaveSupplierWithNullOptionalFields() {
        Supplier supplier = new Supplier(testCode, "Minimal Supplier", null, null, null, null);

        Supplier saved = repository.save(supplier);

        assertNotNull(saved.getId());
        assertEquals("Minimal Supplier", saved.getName());
        assertNull(saved.getContactName());
        assertNull(saved.getPhone());
        assertNull(saved.getEmail());
        assertNull(saved.getAddress());

        // Verify from database
        Optional<Supplier> fromDb = repository.findBySupplierCode(testCode);
        assertTrue(fromDb.isPresent());
        assertNull(fromDb.get().getContactName());
        assertNull(fromDb.get().getPhone());
        assertNull(fromDb.get().getEmail());
        assertNull(fromDb.get().getAddress());
    }

    @Test
    @DisplayName("Should reject save when supplier already has an ID")
    void shouldRejectSaveWhenSupplierAlreadyHasId() {
        Supplier supplier = new Supplier(999L, testCode, "Pre-ID Supplier",
                null, null, null, null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                repository.save(supplier));
        assertTrue(ex.getMessage().contains("already has an ID"));

        assertTrue(repository.findBySupplierCode(testCode).isEmpty());
    }

    @Test
    @DisplayName("Should reject save when supplier code already exists in database")
    void shouldRejectDuplicateSupplierCode() {
        repository.save(new Supplier(testCode, "First Supplier", null, null, null, null));

        ValidationException ex = assertThrows(ValidationException.class, () ->
                repository.save(new Supplier(testCode, "Second Supplier", null, null, null, null)));
        assertTrue(ex.getMessage().contains("already exists"));
    }

    @Test
    @DisplayName("Should find existing supplier by code with all fields mapped")
    void shouldFindExistingSupplierByCode() {
        Supplier saved = repository.save(new Supplier(testCode, "Find Test",
                "Jane Doe", "555-9876", "jane@test.com", "456 Oak Ave"));

        Optional<Supplier> foundOpt = repository.findBySupplierCode(testCode);

        assertTrue(foundOpt.isPresent());
        Supplier found = foundOpt.get();
        assertEquals(saved.getId(), found.getId());
        assertEquals(testCode, found.getSupplierCode());
        assertEquals("Find Test", found.getName());
        assertEquals("Jane Doe", found.getContactName());
        assertEquals("555-9876", found.getPhone());
        assertEquals("jane@test.com", found.getEmail());
        assertEquals("456 Oak Ave", found.getAddress());
    }

    @Test
    @DisplayName("Should return empty Optional for nonexistent supplier code")
    void shouldReturnEmptyForNonexistentCode() {
        assertTrue(repository.findBySupplierCode("NON-EXIST-" + System.currentTimeMillis()).isEmpty());
    }

    @Test
    @DisplayName("Should return empty Optional for null and blank supplier code")
    void shouldReturnEmptyForNullAndBlankCode() {
        assertTrue(repository.findBySupplierCode(null).isEmpty());
        assertTrue(repository.findBySupplierCode("").isEmpty());
        assertTrue(repository.findBySupplierCode("   ").isEmpty());
    }

    @Test
    @DisplayName("Should return true when supplier code exists")
    void shouldReturnTrueWhenCodeExists() {
        repository.save(new Supplier(testCode, "Exists Test", null, null, null, null));
        assertTrue(repository.existsBySupplierCode(testCode));
    }

    @Test
    @DisplayName("Should return false when supplier code does not exist")
    void shouldReturnFalseWhenCodeDoesNotExist() {
        assertFalse(repository.existsBySupplierCode("NON-EXIST-" + System.currentTimeMillis()));
    }

    @Test
    @DisplayName("Should return false for null and blank existence check")
    void shouldReturnFalseForNullAndBlankExistenceCheck() {
        assertFalse(repository.existsBySupplierCode(null));
        assertFalse(repository.existsBySupplierCode(""));
        assertFalse(repository.existsBySupplierCode("   "));
    }

    @Test
    @DisplayName("Should return all suppliers ordered by ID")
    void shouldFindAllSuppliersOrderedById() {
        Supplier saved1 = repository.save(new Supplier(testCode, "First", null, null, null, null));
        Supplier saved2 = repository.save(new Supplier(testCode2, "Second", null, null, null, null));

        List<Supplier> all = repository.findAll();

        assertNotNull(all);
        assertFalse(all.isEmpty());

        Optional<Supplier> found1 = all.stream()
                .filter(s -> testCode.equals(s.getSupplierCode())).findFirst();
        Optional<Supplier> found2 = all.stream()
                .filter(s -> testCode2.equals(s.getSupplierCode())).findFirst();

        assertTrue(found1.isPresent());
        assertTrue(found2.isPresent());
        assertEquals(saved1.getId(), found1.get().getId());
        assertEquals(saved2.getId(), found2.get().getId());

        // Verify ordering
        int idx1 = -1, idx2 = -1;
        for (int i = 0; i < all.size(); i++) {
            if (testCode.equals(all.get(i).getSupplierCode())) idx1 = i;
            if (testCode2.equals(all.get(i).getSupplierCode())) idx2 = i;
        }
        assertTrue(idx1 < idx2, "First saved supplier should appear before second");
    }

    @Test
    @DisplayName("Should update existing supplier name and contact fields")
    void shouldUpdateExistingSupplier() {
        Supplier saved = repository.save(new Supplier(testCode, "Original Name",
                "Old Contact", "111", "old@test.com", "Old Addr"));
        Long originalId = saved.getId();

        Supplier toUpdate = new Supplier(testCode, "Updated Name",
                "New Contact", "222", "new@test.com", "New Addr");

        boolean updated = repository.update(toUpdate);
        assertTrue(updated);

        Optional<Supplier> fromDb = repository.findBySupplierCode(testCode);
        assertTrue(fromDb.isPresent());
        Supplier result = fromDb.get();

        assertEquals(originalId, result.getId(), "ID must remain unchanged");
        assertEquals(testCode, result.getSupplierCode(), "Code must remain unchanged");
        assertEquals("Updated Name", result.getName());
        assertEquals("New Contact", result.getContactName());
        assertEquals("222", result.getPhone());
        assertEquals("new@test.com", result.getEmail());
        assertEquals("New Addr", result.getAddress());
    }

    @Test
    @DisplayName("Should update optional fields to null")
    void shouldUpdateOptionalFieldsToNull() {
        repository.save(new Supplier(testCode, "Name", "Contact", "555", "a@b.com", "Addr"));

        Supplier toUpdate = new Supplier(testCode, "Name", null, null, null, null);
        boolean updated = repository.update(toUpdate);
        assertTrue(updated);

        Supplier fromDb = repository.findBySupplierCode(testCode).orElseThrow();
        assertNull(fromDb.getContactName());
        assertNull(fromDb.getPhone());
        assertNull(fromDb.getEmail());
        assertNull(fromDb.getAddress());
    }

    @Test
    @DisplayName("Should return false when updating non-existent supplier")
    void shouldReturnFalseWhenUpdatingNonexistent() {
        Supplier supplier = new Supplier("NON-EXIST-" + System.currentTimeMillis(),
                "Name", null, null, null, null);
        assertFalse(repository.update(supplier));
    }

    @Test
    @DisplayName("Should delete existing supplier by code")
    void shouldDeleteExistingSupplierByCode() {
        repository.save(new Supplier(testCode, "Delete Test", null, null, null, null));
        assertTrue(repository.existsBySupplierCode(testCode));

        boolean deleted = repository.deleteBySupplierCode(testCode);
        assertTrue(deleted);
        assertFalse(repository.existsBySupplierCode(testCode));
        assertTrue(repository.findBySupplierCode(testCode).isEmpty());
    }

    @Test
    @DisplayName("Should return false when deleting non-existent supplier")
    void shouldReturnFalseWhenDeletingNonexistent() {
        assertFalse(repository.deleteBySupplierCode("NON-EXIST-" + System.currentTimeMillis()));
    }

    @Test
    @DisplayName("Should return false when deleting with null or blank code")
    void shouldReturnFalseWhenDeletingNullOrBlank() {
        assertFalse(repository.deleteBySupplierCode(null));
        assertFalse(repository.deleteBySupplierCode(""));
        assertFalse(repository.deleteBySupplierCode("   "));
    }

    @Test
    @DisplayName("Should persist changes across separate database connections")
    void shouldPersistChangesAcrossConnections() {
        // Save with one repository instance
        JdbcSupplierRepository repo1 = new JdbcSupplierRepository();
        repo1.save(new Supplier(testCode, "Persist Test", "Contact",
                "555", "persist@test.com", "Addr"));

        // Read with another repository instance
        JdbcSupplierRepository repo2 = new JdbcSupplierRepository();
        Optional<Supplier> found = repo2.findBySupplierCode(testCode);

        assertTrue(found.isPresent());
        assertEquals("Persist Test", found.get().getName());
        assertEquals("Contact", found.get().getContactName());
    }

    @Test
    @DisplayName("Should find existing supplier by ID in PostgreSQL")
    void shouldFindExistingSupplierByIdInPostgreSql() {
        Supplier saved = repository.save(new Supplier(testCode, "Find ID Supplier", "Contact", "555", "id@test.com", "City"));

        Optional<Supplier> found = repository.findById(saved.getId());

        assertTrue(found.isPresent());
        assertEquals(saved.getId(), found.get().getId());
        assertEquals(testCode, found.get().getSupplierCode());
        assertEquals("Find ID Supplier", found.get().getName());
    }

    @Test
    @DisplayName("Should return empty Optional when finding by nonexistent, null, or negative ID in PostgreSQL")
    void shouldReturnEmptyForNonexistentIdInPostgreSql() {
        assertTrue(repository.findById(999_999_999L).isEmpty());
        assertTrue(repository.findById(null).isEmpty());
        assertTrue(repository.findById(-1L).isEmpty());
        assertTrue(repository.findById(0L).isEmpty());
    }

    @Test
    @DisplayName("Should prevent deleting supplier when referenced by a product via foreign key in PostgreSQL")
    void shouldPreventDeletingSupplierWhenReferencedByProductInPostgreSql() {
        Supplier supplier = repository.save(new Supplier(testCode, "Referenced Supplier", null, null, null, null));
        String productSku = "PROD-FK-" + System.currentTimeMillis();
        extraTestSkus.add(productSku);

        // Insert product referencing this supplier
        JdbcProductRepository productRepo = new JdbcProductRepository();
        Product product = new Product(productSku, "FK Product", new java.math.BigDecimal("19.99"), 10, 2, supplier.getId());
        productRepo.save(product);

        // Deleting the supplier must fail with ValidationException (due to foreign key RESTRICT)
        ValidationException ex = assertThrows(ValidationException.class, () ->
                repository.deleteBySupplierCode(testCode));
        assertTrue(ex.getMessage().contains("currently assigned to one or more products"));

        // Supplier must still exist in PostgreSQL
        assertTrue(repository.existsBySupplierCode(testCode));

        // Clear assignment from product
        productRepo.updateSupplier(productSku, null);

        // Deleting supplier should now succeed
        assertTrue(repository.deleteBySupplierCode(testCode));
        assertFalse(repository.existsBySupplierCode(testCode));
    }
}
