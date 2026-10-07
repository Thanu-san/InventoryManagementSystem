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
import java.util.Optional;

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

    @Test
    @DisplayName("Should find product by SKU when product exists")
    void shouldFindProductBySkuWhenExists() {
        productService.createProduct("SKU-001", "Keyboard", new BigDecimal("25.00"), 10, 5);

        Optional<Product> result = productService.findProductBySku("SKU-001");

        assertTrue(result.isPresent());
        assertEquals("SKU-001", result.get().getSku());
        assertEquals("Keyboard", result.get().getName());
    }

    @Test
    @DisplayName("Should return empty Optional when SKU does not exist")
    void shouldReturnEmptyWhenSkuDoesNotExist() {
        Optional<Product> result = productService.findProductBySku("SKU-999");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should throw ValidationException when finding by blank SKU")
    void shouldFailWhenFindingByBlankSku() {
        ValidationException exception = assertThrows(ValidationException.class, () ->
                productService.findProductBySku("   ")
        );
        assertTrue(exception.getMessage().contains("SKU cannot be blank"));
    }

    @Test
    @DisplayName("Should find product by SKU when input has surrounding spaces")
    void shouldFindProductBySkuWithSurroundingSpaces() {
        productService.createProduct("SKU-001", "Keyboard", new BigDecimal("25.00"), 10, 5);

        Optional<Product> result = productService.findProductBySku("  SKU-001  ");

        assertTrue(result.isPresent());
        assertEquals("SKU-001", result.get().getSku());
    }

    @Test
    @DisplayName("Should successfully update product name")
    void shouldUpdateProductNameSuccessfully() {
        productService.createProduct("SKU-001", "Keyboard", new BigDecimal("25.00"), 10, 5);

        Product updated = productService.updateProduct("SKU-001", "Mechanical Keyboard", new BigDecimal("25.00"), 5);

        assertEquals("Mechanical Keyboard", updated.getName());
    }

    @Test
    @DisplayName("Should successfully update product price")
    void shouldUpdateProductPriceSuccessfully() {
        productService.createProduct("SKU-001", "Keyboard", new BigDecimal("25.00"), 10, 5);

        Product updated = productService.updateProduct("SKU-001", "Keyboard", new BigDecimal("35.00"), 5);

        assertEquals(new BigDecimal("35.00"), updated.getPrice());
    }

    @Test
    @DisplayName("Should successfully update minimum stock")
    void shouldUpdateMinimumStockSuccessfully() {
        productService.createProduct("SKU-001", "Keyboard", new BigDecimal("25.00"), 10, 5);

        Product updated = productService.updateProduct("SKU-001", "Keyboard", new BigDecimal("25.00"), 8);

        assertEquals(8, updated.getMinimumStock());
    }

    @Test
    @DisplayName("Should update multiple editable fields while preserving ID, SKU, and Quantity")
    void shouldUpdateMultipleFieldsAndPreserveInvariants() {
        Product original = productService.createProduct("SKU-001", "Keyboard", new BigDecimal("25.00"), 10, 5);
        Long originalId = original.getId();

        Product updated = productService.updateProduct("SKU-001", "Pro Keyboard", new BigDecimal("49.99"), 2);

        // Editable fields are updated
        assertEquals("Pro Keyboard", updated.getName());
        assertEquals(new BigDecimal("49.99"), updated.getPrice());
        assertEquals(2, updated.getMinimumStock());

        // Invariant fields remain strictly unchanged
        assertEquals(originalId, updated.getId());
        assertEquals("SKU-001", updated.getSku());
        assertEquals(10, updated.getQuantity());
    }

    @Test
    @DisplayName("Should throw ValidationException when updating non-existent SKU")
    void shouldFailWhenUpdatingNonExistentSku() {
        ValidationException exception = assertThrows(ValidationException.class, () ->
                productService.updateProduct("SKU-999", "Mouse", new BigDecimal("15.00"), 2)
        );
        assertTrue(exception.getMessage().contains("Product not found"));
    }

    @Test
    @DisplayName("Should throw ValidationException when updating with blank SKU")
    void shouldFailWhenUpdatingWithBlankSku() {
        ValidationException exception = assertThrows(ValidationException.class, () ->
                productService.updateProduct("   ", "Mouse", new BigDecimal("15.00"), 2)
        );
        assertTrue(exception.getMessage().contains("SKU cannot be blank"));
    }

    @Test
    @DisplayName("Should throw ValidationException when updating with blank name")
    void shouldFailWhenUpdatingWithBlankName() {
        productService.createProduct("SKU-001", "Keyboard", new BigDecimal("25.00"), 10, 5);

        ValidationException exception = assertThrows(ValidationException.class, () ->
                productService.updateProduct("SKU-001", "   ", new BigDecimal("25.00"), 5)
        );
        assertTrue(exception.getMessage().contains("name cannot be blank"));
    }

    @Test
    @DisplayName("Should throw ValidationException when updating with negative price")
    void shouldFailWhenUpdatingWithNegativePrice() {
        productService.createProduct("SKU-001", "Keyboard", new BigDecimal("25.00"), 10, 5);

        ValidationException exception = assertThrows(ValidationException.class, () ->
                productService.updateProduct("SKU-001", "Keyboard", new BigDecimal("-1.00"), 5)
        );
        assertTrue(exception.getMessage().contains("price must be greater than or equal to 0"));
    }

    @Test
    @DisplayName("Should throw ValidationException when updating with negative minimum stock")
    void shouldFailWhenUpdatingWithNegativeMinimumStock() {
        productService.createProduct("SKU-001", "Keyboard", new BigDecimal("25.00"), 10, 5);

        ValidationException exception = assertThrows(ValidationException.class, () ->
                productService.updateProduct("SKU-001", "Keyboard", new BigDecimal("25.00"), -1)
        );
        assertTrue(exception.getMessage().contains("Minimum stock must be greater than or equal to 0"));
    }

    @Test
    @DisplayName("Should successfully delete existing product by SKU")
    void shouldDeleteExistingProductBySku() {
        productService.createProduct("SKU-001", "Keyboard", new BigDecimal("25.00"), 10, 5);

        productService.deleteProductBySku("SKU-001");

        Optional<Product> found = productService.findProductBySku("SKU-001");
        assertTrue(found.isEmpty());
    }

    @Test
    @DisplayName("Should throw ValidationException when deleting non-existent SKU")
    void shouldFailWhenDeletingNonExistentSku() {
        ValidationException exception = assertThrows(ValidationException.class, () ->
                productService.deleteProductBySku("SKU-999")
        );
        assertTrue(exception.getMessage().contains("Product not found"));
    }

    @Test
    @DisplayName("Should throw ValidationException when deleting with blank SKU")
    void shouldFailWhenDeletingWithBlankSku() {
        ValidationException exception = assertThrows(ValidationException.class, () ->
                productService.deleteProductBySku("   ")
        );
        assertTrue(exception.getMessage().contains("SKU cannot be blank"));
    }

    @Test
    @DisplayName("Should verify deleted product is removed from product listing")
    void shouldRemoveDeletedProductFromAllProducts() {
        productService.createProduct("SKU-001", "Keyboard", new BigDecimal("25.00"), 10, 5);
        assertEquals(1, productService.getAllProducts().size());

        productService.deleteProductBySku("SKU-001");

        assertEquals(0, productService.getAllProducts().size());
    }

    @Test
    @DisplayName("Should not affect other products when one product is deleted")
    void shouldNotAffectOtherProductsWhenOneIsDeleted() {
        productService.createProduct("SKU-001", "Keyboard", new BigDecimal("25.00"), 10, 5);
        productService.createProduct("SKU-002", "Mouse", new BigDecimal("15.00"), 20, 5);

        productService.deleteProductBySku("SKU-001");

        assertTrue(productService.findProductBySku("SKU-001").isEmpty());
        Optional<Product> remaining = productService.findProductBySku("SKU-002");
        assertTrue(remaining.isPresent());
        assertEquals("Mouse", remaining.get().getName());
        assertEquals(1, productService.getAllProducts().size());
    }

    @Test
    @DisplayName("Should successfully delete product when SKU has surrounding spaces")
    void shouldDeleteProductWhenSkuHasSurroundingSpaces() {
        productService.createProduct("SKU-001", "Keyboard", new BigDecimal("25.00"), 10, 5);

        productService.deleteProductBySku("  SKU-001  ");

        Optional<Product> found = productService.findProductBySku("SKU-001");
        assertTrue(found.isEmpty());
    }
}
