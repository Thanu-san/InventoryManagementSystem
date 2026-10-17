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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JdbcProductRepository Integration Tests")
class JdbcProductRepositoryTest {

    private JdbcProductRepository repository;
    private String testSku;

    @BeforeEach
    void setUp() {
        repository = new JdbcProductRepository();
        testSku = "TEST-" + System.currentTimeMillis();
    }

    @AfterEach
    void tearDown() {
        // Clean up test product by SKU to keep database clean
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM products WHERE sku = ?")) {
            statement.setString(1, testSku);
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
    @DisplayName("Should throw UnsupportedOperationException for remaining unimplemented methods")
    void shouldThrowForUnimplementedMethods() {
        assertThrows(UnsupportedOperationException.class, () -> repository.existsBySku("ANY"));
        assertThrows(UnsupportedOperationException.class, () -> repository.findAll());
        assertThrows(UnsupportedOperationException.class, () -> repository.deleteBySku("ANY"));
    }
}
