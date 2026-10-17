package com.stockflow.repository;

import com.stockflow.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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
    @DisplayName("Should return false when updating with null product")
    void shouldReturnFalseWhenUpdatingNull() {
        assertFalse(repository.update(null), "update() should return false when product is null");
    }
}
