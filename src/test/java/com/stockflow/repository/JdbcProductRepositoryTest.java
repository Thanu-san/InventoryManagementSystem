package com.stockflow.repository;

import com.stockflow.database.DatabaseConnection;
import com.stockflow.model.Product;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JdbcProductRepository Integration Tests")
class JdbcProductRepositoryTest {

    private JdbcProductRepository repository;
    private String testSku;
    private String testSku2;

    @BeforeEach
    void setUp() {
        repository = new JdbcProductRepository();
        testSku = "TEST-" + System.currentTimeMillis();
        testSku2 = "TEST-2-" + System.currentTimeMillis();
    }

    @AfterEach
    void tearDown() {
        // Clean up test products by SKU to keep database clean
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM products WHERE sku = ? OR sku = ?")) {
            statement.setString(1, testSku);
            statement.setString(2, testSku2);
            statement.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    @Test
    @DisplayName("Should successfully insert product into PostgreSQL and assign generated ID")
    void shouldSaveProductAndAssignGeneratedId() {
        Product product = new Product(
                testSku,
                "Test Mechanical Keyboard",
                new BigDecimal("89.99"),
                15,
                3
        );

        assertNull(product.getId(), "Product should not have an ID before save");

        Product savedProduct = repository.save(product);

        assertSame(product, savedProduct, "save() should return the same Product instance");
        assertNotNull(savedProduct.getId(), "PostgreSQL generated ID must be assigned");
        assertTrue(savedProduct.getId() > 0, "Generated ID must be positive");
        assertEquals(testSku, savedProduct.getSku());
        assertEquals("Test Mechanical Keyboard", savedProduct.getName());
        assertEquals(new BigDecimal("89.99"), savedProduct.getPrice());
        assertEquals(15, savedProduct.getQuantity());
        assertEquals(3, savedProduct.getMinimumStock());
    }

    @Test
    @DisplayName("Should successfully find an existing product by SKU with all fields mapped")
    void shouldFindExistingProductBySku() {
        Product product = new Product(
                testSku,
                "Test Mechanical Keyboard",
                new BigDecimal("89.99"),
                15,
                3
        );
        Product savedProduct = repository.save(product);

        Optional<Product> foundOpt = repository.findBySku(testSku);

        assertTrue(foundOpt.isPresent(), "Product should be found by its SKU");
        Product found = foundOpt.get();
        assertEquals(savedProduct.getId(), found.getId(), "ID should match generated database ID");
        assertEquals(testSku, found.getSku(), "SKU should match");
        assertEquals("Test Mechanical Keyboard", found.getName(), "Name should match");
        assertEquals(new BigDecimal("89.99"), found.getPrice(), "Price should match");
        assertEquals(15, found.getQuantity(), "Quantity should match");
        assertEquals(3, found.getMinimumStock(), "Minimum stock should match");
    }

    @Test
    @DisplayName("Should return empty Optional when searching for nonexistent SKU")
    void shouldReturnEmptyOptionalWhenSkuDoesNotExist() {
        Optional<Product> foundOpt = repository.findBySku("NON-EXISTENT-" + System.currentTimeMillis());

        assertTrue(foundOpt.isEmpty(), "Should return Optional.empty() for nonexistent SKU");
    }

    @Test
    @DisplayName("Should return true when checking existence of existing SKU")
    void shouldReturnTrueWhenSkuExists() {
        Product product = new Product(
                testSku,
                "Test Mechanical Keyboard",
                new BigDecimal("89.99"),
                15,
                3
        );
        repository.save(product);

        boolean exists = repository.existsBySku(testSku);

        assertTrue(exists, "existsBySku should return true for existing product");
    }

    @Test
    @DisplayName("Should return false when checking existence of nonexistent SKU")
    void shouldReturnFalseWhenSkuDoesNotExist() {
        boolean exists = repository.existsBySku("NON-EXISTENT-" + System.currentTimeMillis());

        assertFalse(exists, "existsBySku should return false for nonexistent product");
    }

    @Test
    @DisplayName("Should return all products ordered by ascending ID with all fields mapped")
    void shouldFindAllProductsOrderedById() {
        Product product1 = new Product(
                testSku,
                "First Test Product",
                new BigDecimal("19.99"),
                10,
                2
        );
        Product product2 = new Product(
                testSku2,
                "Second Test Product",
                new BigDecimal("49.99"),
                20,
                5
        );

        Product saved1 = repository.save(product1);
        Product saved2 = repository.save(product2);

        List<Product> allProducts = repository.findAll();

        assertNotNull(allProducts, "findAll() should not return null");
        assertFalse(allProducts.isEmpty(), "findAll() should contain saved products");

        // Locate the inserted test products in the returned list
        Optional<Product> found1 = allProducts.stream()
                .filter(p -> testSku.equals(p.getSku()))
                .findFirst();
        Optional<Product> found2 = allProducts.stream()
                .filter(p -> testSku2.equals(p.getSku()))
                .findFirst();

        assertTrue(found1.isPresent(), "First product should be present in findAll() results");
        assertTrue(found2.isPresent(), "Second product should be present in findAll() results");

        Product retrieved1 = found1.get();
        assertEquals(saved1.getId(), retrieved1.getId());
        assertEquals(testSku, retrieved1.getSku());
        assertEquals("First Test Product", retrieved1.getName());
        assertEquals(new BigDecimal("19.99"), retrieved1.getPrice());
        assertEquals(10, retrieved1.getQuantity());
        assertEquals(2, retrieved1.getMinimumStock());

        Product retrieved2 = found2.get();
        assertEquals(saved2.getId(), retrieved2.getId());
        assertEquals(testSku2, retrieved2.getSku());
        assertEquals("Second Test Product", retrieved2.getName());
        assertEquals(new BigDecimal("49.99"), retrieved2.getPrice());
        assertEquals(20, retrieved2.getQuantity());
        assertEquals(5, retrieved2.getMinimumStock());

        // Verify that products are ordered by ascending ID
        int index1 = -1;
        int index2 = -1;
        for (int i = 0; i < allProducts.size(); i++) {
            if (testSku.equals(allProducts.get(i).getSku())) {
                index1 = i;
            }
            if (testSku2.equals(allProducts.get(i).getSku())) {
                index2 = i;
            }
        }
        assertTrue(index1 < index2, "Product inserted earlier (lower ID) should appear before product inserted later (higher ID)");

        // Verify the entire result list is sorted in non-decreasing order of ID
        for (int i = 0; i < allProducts.size() - 1; i++) {
            assertTrue(allProducts.get(i).getId() <= allProducts.get(i + 1).getId(),
                    "All products must be returned in ascending ID order");
        }
    }

    @Test
    @DisplayName("Should throw UnsupportedOperationException for remaining unimplemented methods")
    void shouldThrowForUnimplementedMethods() {
        assertThrows(UnsupportedOperationException.class, () -> repository.deleteBySku("ANY"));
    }
}
