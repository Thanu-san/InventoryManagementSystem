package com.stockflow.repository;

import com.stockflow.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InMemoryProductRepository Tests")
class InMemoryProductRepositoryTest {

    private InMemoryProductRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryProductRepository();
    }

    @Test
    @DisplayName("Should successfully update existing product in memory")
    void shouldUpdateExistingProduct() {
        Product product = new Product("SKU-001", "Keyboard", new BigDecimal("25.00"), 10, 5);
        repository.save(product);

        Product updatedProduct = new Product(product.getId(), "SKU-001", "Mechanical Keyboard", new BigDecimal("45.00"), 10, 8);
        boolean updated = repository.update(updatedProduct);

        assertTrue(updated, "update() should return true for existing product");
        Optional<Product> foundOpt = repository.findBySku("SKU-001");
        assertTrue(foundOpt.isPresent());
        assertEquals("Mechanical Keyboard", foundOpt.get().getName());
        assertEquals(new BigDecimal("45.00"), foundOpt.get().getPrice());
        assertEquals(8, foundOpt.get().getMinimumStock());
    }

    @Test
    @DisplayName("Should return false when updating non-existent product in memory")
    void shouldReturnFalseWhenUpdatingNonExistentProduct() {
        Product nonExistent = new Product("SKU-999", "Mouse", new BigDecimal("15.00"), 5, 2);
        boolean updated = repository.update(nonExistent);

        assertFalse(updated, "update() should return false for nonexistent product");
    }

    @Test
    @DisplayName("Should return false when updating with null product or null SKU")
    void shouldReturnFalseWhenUpdatingNull() {
        assertFalse(repository.update(null), "update() should return false when product is null");
    }

    @Test
    @DisplayName("Should update only editable fields and preserve stored ID, SKU, and quantity even if input differs")
    void shouldUpdateOnlyEditableFieldsAndPreserveInvariants() {
        Product original = new Product("SKU-INV", "Original Item", new BigDecimal("10.00"), 50, 5);
        Product saved = repository.save(original);
        Long originalId = saved.getId();

        // Create update object with completely different ID and quantity
        Product toUpdate = new Product(9999L, "SKU-INV", "Modified Item", new BigDecimal("19.99"), 100, 15);

        boolean updated = repository.update(toUpdate);
        assertTrue(updated, "update() should return true for existing product");

        Optional<Product> storedOpt = repository.findBySku("SKU-INV");
        assertTrue(storedOpt.isPresent());
        Product stored = storedOpt.get();

        // Editable fields must be updated
        assertEquals("Modified Item", stored.getName());
        assertEquals(new BigDecimal("19.99"), stored.getPrice());
        assertEquals(15, stored.getMinimumStock());

        // Invariants must strictly remain preserved
        assertEquals(originalId, stored.getId(), "Stored ID must remain unchanged");
        assertEquals("SKU-INV", stored.getSku(), "Stored SKU must remain unchanged");
        assertEquals(50, stored.getQuantity(), "Stored quantity must remain unchanged");
    }

    @Test
    @DisplayName("Should reject duplicate save attempts and preserve original product without overwriting")
    void shouldRejectDuplicateSaveAndPreserveOriginalProduct() {
        Product original = new Product("SKU-DUP", "Original Keyboard", new BigDecimal("25.00"), 10, 5);
        Product saved = repository.save(original);
        Long originalId = saved.getId();

        Product duplicate = new Product("SKU-DUP", "Overwritten Keyboard", new BigDecimal("99.99"), 99, 1);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                repository.save(duplicate)
        );
        assertTrue(exception.getMessage().contains("already exists"));

        // Verify the original record remains completely untouched
        Optional<Product> storedOpt = repository.findBySku("SKU-DUP");
        assertTrue(storedOpt.isPresent());
        Product stored = storedOpt.get();
        assertEquals(originalId, stored.getId());
        assertEquals("Original Keyboard", stored.getName());
        assertEquals(new BigDecimal("25.00"), stored.getPrice());
        assertEquals(10, stored.getQuantity());
        assertEquals(5, stored.getMinimumStock());
    }

    @Test
    @DisplayName("Should reject save when product already has an assigned ID")
    void shouldRejectSaveWhenProductAlreadyHasId() {
        Product productWithId = new Product(99L, "SKU-HAS-ID", "Existing Item", new BigDecimal("15.00"), 10, 2);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                repository.save(productWithId)
        );
        assertTrue(exception.getMessage().contains("already has an ID"));
        assertTrue(repository.findBySku("SKU-HAS-ID").isEmpty(), "Repository must not store rejected product");
    }

    @Test
    @DisplayName("Should return products where quantity is below minimum stock")
    void shouldReturnProductsBelowMinimumStock() {
        repository.save(new Product("SKU-BELOW", "Below Min", new BigDecimal("10.00"), 2, 5));
        repository.save(new Product("SKU-ABOVE", "Above Min", new BigDecimal("10.00"), 10, 5));

        List<Product> lowStock = repository.findLowStockProducts();
        assertEquals(1, lowStock.size());
        assertEquals("SKU-BELOW", lowStock.get(0).getSku());
    }

    @Test
    @DisplayName("Should return products where quantity is exactly equal to minimum stock")
    void shouldReturnProductsEqualToMinimumStock() {
        repository.save(new Product("SKU-EQUAL", "Equal Min", new BigDecimal("15.00"), 5, 5));
        repository.save(new Product("SKU-ABOVE", "Above Min", new BigDecimal("15.00"), 6, 5));

        List<Product> lowStock = repository.findLowStockProducts();
        assertEquals(1, lowStock.size());
        assertEquals("SKU-EQUAL", lowStock.get(0).getSku());
    }

    @Test
    @DisplayName("Should exclude products where quantity is strictly above minimum stock")
    void shouldExcludeProductsAboveMinimumStock() {
        repository.save(new Product("SKU-OK1", "Item 1", new BigDecimal("20.00"), 10, 5));
        repository.save(new Product("SKU-OK2", "Item 2", new BigDecimal("20.00"), 25, 10));

        List<Product> lowStock = repository.findLowStockProducts();
        assertTrue(lowStock.isEmpty(), "Should return empty list when no products are low stock");
    }

    @Test
    @DisplayName("Should return empty list when repository has no products")
    void shouldReturnEmptyListWhenNoProducts() {
        List<Product> lowStock = repository.findLowStockProducts();
        assertNotNull(lowStock);
        assertTrue(lowStock.isEmpty());
    }

    @Test
    @DisplayName("Should order low-stock products by quantity ascending, then SKU ascending")
    void shouldOrderLowStockProductsCorrectly() {
        repository.save(new Product("SKU-C", "Item C", new BigDecimal("10.00"), 3, 5));
        repository.save(new Product("SKU-A", "Item A", new BigDecimal("10.00"), 1, 5));
        repository.save(new Product("SKU-B", "Item B", new BigDecimal("10.00"), 1, 5));
        repository.save(new Product("SKU-D", "Item D", new BigDecimal("10.00"), 10, 5)); // excluded

        List<Product> lowStock = repository.findLowStockProducts();
        assertEquals(3, lowStock.size());
        assertEquals("SKU-A", lowStock.get(0).getSku()); // qty 1, SKU-A
        assertEquals("SKU-B", lowStock.get(1).getSku()); // qty 1, SKU-B
        assertEquals("SKU-C", lowStock.get(2).getSku()); // qty 3, SKU-C
    }
}
