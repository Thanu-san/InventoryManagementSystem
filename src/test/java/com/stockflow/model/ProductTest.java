package com.stockflow.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Product Domain Model Encapsulation Tests")
class ProductTest {

    @Test
    @DisplayName("Should successfully construct a product with valid state")
    void shouldConstructValidProduct() {
        Product product = new Product("SKU-100", "Monitor", new BigDecimal("199.99"), 5, 2);

        assertEquals("SKU-100", product.getSku());
        assertEquals("Monitor", product.getName());
        assertEquals(new BigDecimal("199.99"), product.getPrice());
        assertEquals(5, product.getQuantity());
        assertEquals(2, product.getMinimumStock());
        assertNull(product.getId());
    }

    @Test
    @DisplayName("Should reject blank SKU in constructor")
    void shouldRejectBlankSku() {
        assertThrows(IllegalArgumentException.class, () ->
                new Product("  ", "Monitor", new BigDecimal("199.99"), 5, 2));
    }

    @Test
    @DisplayName("Should reject blank name in constructor and setter")
    void shouldRejectBlankName() {
        assertThrows(IllegalArgumentException.class, () ->
                new Product("SKU-100", "   ", new BigDecimal("199.99"), 5, 2));

        Product product = new Product("SKU-100", "Monitor", new BigDecimal("199.99"), 5, 2);
        assertThrows(IllegalArgumentException.class, () -> product.setName(""));
    }

    @Test
    @DisplayName("Should reject negative price in constructor and setter")
    void shouldRejectNegativePrice() {
        assertThrows(IllegalArgumentException.class, () ->
                new Product("SKU-100", "Monitor", new BigDecimal("-0.01"), 5, 2));

        Product product = new Product("SKU-100", "Monitor", new BigDecimal("199.99"), 5, 2);
        assertThrows(IllegalArgumentException.class, () -> product.setPrice(new BigDecimal("-5.00")));
    }

    @Test
    @DisplayName("Should reject negative quantity in constructor")
    void shouldRejectNegativeQuantity() {
        assertThrows(IllegalArgumentException.class, () ->
                new Product("SKU-100", "Monitor", new BigDecimal("199.99"), -1, 2));
    }

    @Test
    @DisplayName("Should reject negative minimum stock in constructor and setter")
    void shouldRejectNegativeMinimumStock() {
        assertThrows(IllegalArgumentException.class, () ->
                new Product("SKU-100", "Monitor", new BigDecimal("199.99"), 5, -1));

        Product product = new Product("SKU-100", "Monitor", new BigDecimal("199.99"), 5, 2);
        assertThrows(IllegalArgumentException.class, () -> product.setMinimumStock(-3));
    }

    @Test
    @DisplayName("Should allow ID to be set once and forbid reassignment")
    void shouldAllowSettingIdOnlyOnce() {
        Product product = new Product("SKU-100", "Monitor", new BigDecimal("199.99"), 5, 2);

        product.setId(10L);
        assertEquals(10L, product.getId());

        // Attempting to overwrite ID should fail
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> product.setId(20L));
        assertTrue(ex.getMessage().contains("already assigned"));
    }

    @Test
    @DisplayName("Should reject zero or negative ID")
    void shouldRejectInvalidId() {
        Product product = new Product("SKU-100", "Monitor", new BigDecimal("199.99"), 5, 2);

        assertThrows(IllegalArgumentException.class, () -> product.setId(0L));
        assertThrows(IllegalArgumentException.class, () -> product.setId(-1L));
        assertThrows(IllegalArgumentException.class, () -> product.setId(null));
    }

    @Test
    @DisplayName("Should construct product with supplier ID")
    void shouldConstructProductWithSupplierId() {
        Product product = new Product("SKU-SUP", "Headphones", new BigDecimal("59.99"), 10, 2, 50L);

        assertEquals(50L, product.getSupplierId());
        assertEquals("SKU-SUP", product.getSku());
    }

    @Test
    @DisplayName("Should construct product with null supplier ID by default")
    void shouldDefaultSupplierIdToNull() {
        Product product = new Product("SKU-NOSUP", "Headphones", new BigDecimal("59.99"), 10, 2);

        assertNull(product.getSupplierId());
    }

    @Test
    @DisplayName("Should allow setting and clearing supplier ID")
    void shouldAllowSettingAndClearingSupplierId() {
        Product product = new Product("SKU-MOD", "Headphones", new BigDecimal("59.99"), 10, 2);

        product.setSupplierId(100L);
        assertEquals(100L, product.getSupplierId());

        product.setSupplierId(null);
        assertNull(product.getSupplierId());
    }

    @Test
    @DisplayName("Should reject zero or negative supplier ID")
    void shouldRejectInvalidSupplierId() {
        Product product = new Product("SKU-VAL", "Headphones", new BigDecimal("59.99"), 10, 2);

        assertThrows(IllegalArgumentException.class, () -> product.setSupplierId(0L));
        assertThrows(IllegalArgumentException.class, () -> product.setSupplierId(-5L));
    }

    @Test
    @DisplayName("Should include supplierId in toString output")
    void shouldIncludeSupplierIdInToString() {
        Product product = new Product("SKU-STR", "Item", new BigDecimal("10.00"), 5, 1, 42L);

        String str = product.toString();
        assertTrue(str.contains("supplierId=42"));
    }
}
