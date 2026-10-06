package com.stockflow.service;

import com.stockflow.exception.ValidationException;
import com.stockflow.model.Product;
import com.stockflow.repository.InMemoryProductRepository;
import com.stockflow.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProductService Business Rule Tests")
class ProductServiceTest {

    private ProductRepository productRepository;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productRepository = new InMemoryProductRepository();
        productService = new ProductService(productRepository);
    }

    @Test
    @DisplayName("Should create product successfully when all inputs are valid")
    void shouldCreateProductSuccessfully() {
        Product product = productService.createProduct(
                "KB-001",
                "Mechanical Keyboard",
                new BigDecimal("79.99"),
                10,
                3
        );

        assertNotNull(product);
        assertEquals("KB-001", product.getSku());
        assertEquals("Mechanical Keyboard", product.getName());
        assertEquals(new BigDecimal("79.99"), product.getPrice());
        assertEquals(10, product.getQuantity());
        assertEquals(3, product.getMinimumStock());
        assertNotNull(product.getId());

        List<Product> all = productService.getAllProducts();
        assertEquals(1, all.size());
    }

    @Test
    @DisplayName("Should throw ValidationException when SKU is blank")
    void shouldFailWhenSkuIsBlank() {
        ValidationException exception = assertThrows(ValidationException.class, () ->
                productService.createProduct(
                        "   ",
                        "Wireless Mouse",
                        new BigDecimal("29.99"),
                        5,
                        2
                )
        );
        assertTrue(exception.getMessage().contains("SKU cannot be blank"));
    }

    @Test
    @DisplayName("Should throw ValidationException when SKU is duplicate")
    void shouldFailWhenSkuIsDuplicate() {
        productService.createProduct("MOU-01", "Mouse", new BigDecimal("15.00"), 5, 2);

        ValidationException exception = assertThrows(ValidationException.class, () ->
                productService.createProduct("MOU-01", "Another Mouse", new BigDecimal("20.00"), 10, 2)
        );
        assertTrue(exception.getMessage().contains("already exists"));
    }

    @Test
    @DisplayName("Should throw ValidationException when Name is blank")
    void shouldFailWhenNameIsBlank() {
        ValidationException exception = assertThrows(ValidationException.class, () ->
                productService.createProduct("MON-01", "", new BigDecimal("199.99"), 3, 1)
        );
        assertTrue(exception.getMessage().contains("name cannot be blank"));
    }

    @Test
    @DisplayName("Should throw ValidationException when Price is negative")
    void shouldFailWhenPriceIsNegative() {
        ValidationException exception = assertThrows(ValidationException.class, () ->
                productService.createProduct("MON-01", "Monitor", new BigDecimal("-10.00"), 3, 1)
        );
        assertTrue(exception.getMessage().contains("price must be greater than or equal to 0"));
    }

    @Test
    @DisplayName("Should throw ValidationException when Quantity is negative")
    void shouldFailWhenQuantityIsNegative() {
        ValidationException exception = assertThrows(ValidationException.class, () ->
                productService.createProduct("MON-01", "Monitor", new BigDecimal("100.00"), -5, 1)
        );
        assertTrue(exception.getMessage().contains("Quantity must be greater than or equal to 0"));
    }

    @Test
    @DisplayName("Should throw ValidationException when Minimum Stock is negative")
    void shouldFailWhenMinimumStockIsNegative() {
        ValidationException exception = assertThrows(ValidationException.class, () ->
                productService.createProduct("MON-01", "Monitor", new BigDecimal("100.00"), 10, -2)
        );
        assertTrue(exception.getMessage().contains("Minimum stock must be greater than or equal to 0"));
    }
}
