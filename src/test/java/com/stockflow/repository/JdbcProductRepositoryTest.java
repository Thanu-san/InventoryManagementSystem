package com.stockflow.repository;

import com.stockflow.database.DatabaseConnection;
import com.stockflow.model.Product;
import com.stockflow.service.ProductService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JdbcProductRepository Integration Tests")
class JdbcProductRepositoryTest {

    private JdbcProductRepository repository;
    private String testSku;
    private String testSku2;
    private final List<String> extraTestSkus = new ArrayList<>();

    @BeforeEach
    void setUp() {
        repository = new JdbcProductRepository();
        testSku = "TEST-" + System.currentTimeMillis();
        testSku2 = "TEST-2-" + System.currentTimeMillis();
        extraTestSkus.clear();
    }

    @AfterEach
    void tearDown() {
        // Clean up test products by SKU to keep database clean
        try (Connection connection = DatabaseConnection.getConnection()) {
            for (String sku : extraTestSkus) {
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM products WHERE sku = ?")) {
                    statement.setString(1, sku);
                    statement.executeUpdate();
                } catch (SQLException ignored) {
                }
            }
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM products WHERE sku = ? OR sku = ?")) {
                statement.setString(1, testSku);
                statement.setString(2, testSku2);
                statement.executeUpdate();
            }
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
    @DisplayName("Should reject save and not insert row into database when product already has an assigned ID")
    void shouldRejectSaveWhenProductAlreadyHasId() {
        Product productWithId = new Product(
                999L,
                testSku,
                "Pre-assigned ID Keyboard",
                new BigDecimal("89.99"),
                15,
                3
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                repository.save(productWithId)
        );
        assertTrue(exception.getMessage().contains("already has an ID"));

        // Verify that NO row was inserted into PostgreSQL
        Optional<Product> foundOpt = repository.findBySku(testSku);
        assertTrue(foundOpt.isEmpty(), "No database row should have been inserted into PostgreSQL");
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
    @DisplayName("Should successfully update product name, price, and minimum stock while preserving ID, SKU, and quantity")
    void shouldUpdateExistingProductAndPreserveInvariants() {
        Product original = new Product(
                testSku,
                "Original Wireless Mouse",
                new BigDecimal("29.99"),
                50,
                5
        );
        Product saved = repository.save(original);
        Long originalId = saved.getId();

        // Create updated product representation with different editable fields AND different quantity/id
        // to verify that quantity and ID in the database cannot be overwritten by update()
        Product toUpdate = new Product(
                999999L,
                testSku,
                "Upgraded Wireless Mouse Pro",
                new BigDecimal("39.99"),
                100, // Different quantity
                10   // Updated minimum stock
        );

        boolean updated = repository.update(toUpdate);
        assertTrue(updated, "update() should return true when an existing product row is updated");

        // Verify state in PostgreSQL database
        Optional<Product> foundOpt = repository.findBySku(testSku);
        assertTrue(foundOpt.isPresent(), "Product should be found in database after update");

        Product fromDb = foundOpt.get();
        // Editable fields must be updated
        assertEquals("Upgraded Wireless Mouse Pro", fromDb.getName());
        assertEquals(new BigDecimal("39.99"), fromDb.getPrice());
        assertEquals(10, fromDb.getMinimumStock());

        // Invariants must remain unchanged in database
        assertEquals(originalId, fromDb.getId(), "Database ID must remain unchanged");
        assertEquals(testSku, fromDb.getSku(), "Product SKU must remain unchanged");
        assertEquals(50, fromDb.getQuantity(), "Quantity must remain unchanged");
    }

    @Test
    @DisplayName("Should return false when attempting to update a nonexistent SKU")
    void shouldReturnFalseWhenUpdatingNonexistentSku() {
        Product nonexistentProduct = new Product(
                "NON-EXISTENT-" + System.currentTimeMillis(),
                "Nonexistent Item",
                new BigDecimal("19.99"),
                10,
                2
        );

        boolean updated = repository.update(nonexistentProduct);
        assertFalse(updated, "update() should return false when no matching row was updated");
    }

    @Test
    @DisplayName("Should successfully update product via ProductService using JdbcProductRepository")
    void shouldSuccessfullyUpdateProductViaServiceWithJdbcRepository() {
        ProductService service = new ProductService(repository);
        service.createProduct(testSku, "Original via Service", new BigDecimal("15.00"), 20, 5);

        Product updated = service.updateProduct(testSku, "Updated via Service", new BigDecimal("25.00"), 8);

        assertEquals("Updated via Service", updated.getName());
        assertEquals(new BigDecimal("25.00"), updated.getPrice());
        assertEquals(8, updated.getMinimumStock());

        Optional<Product> foundOpt = repository.findBySku(testSku);
        assertTrue(foundOpt.isPresent());
        Product fromDb = foundOpt.get();
        assertEquals("Updated via Service", fromDb.getName());
        assertEquals(new BigDecimal("25.00"), fromDb.getPrice());
        assertEquals(8, fromDb.getMinimumStock());
        assertEquals(20, fromDb.getQuantity(), "Quantity must remain untouched");
    }

    @Test
    @DisplayName("Should successfully delete existing product by SKU")
    void shouldSuccessfullyDeleteExistingProductBySku() {
        Product product = new Product(
                testSku,
                "Product To Delete",
                new BigDecimal("15.50"),
                10,
                2
        );
        repository.save(product);

        // Verify product was inserted
        assertTrue(repository.existsBySku(testSku), "Product should exist before deletion");

        // Execute delete
        boolean deleted = repository.deleteBySku(testSku);
        assertTrue(deleted, "deleteBySku() should return true when a product was deleted");

        // Verify product is no longer in the database
        assertFalse(repository.existsBySku(testSku), "Product should no longer exist in database");
        Optional<Product> foundOpt = repository.findBySku(testSku);
        assertTrue(foundOpt.isEmpty(), "findBySku() should return empty for deleted product");
    }

    @Test
    @DisplayName("Should return false when attempting to delete a nonexistent SKU")
    void shouldReturnFalseWhenDeletingNonexistentSku() {
        boolean deleted = repository.deleteBySku("NON-EXISTENT-" + System.currentTimeMillis());

        assertFalse(deleted, "deleteBySku() should return false when no product matched the SKU");
    }

    @Test
    @DisplayName("Should retrieve only low-stock products from PostgreSQL ordered by quantity ASC, sku ASC")
    void shouldFindLowStockProductsInPostgreSqlOrderedCorrectly() {
        String skuBelow = "LOW-BELOW-" + System.currentTimeMillis();
        String skuEqual = "LOW-EQUAL-" + System.currentTimeMillis();
        String skuAbove = "LOW-ABOVE-" + System.currentTimeMillis();

        extraTestSkus.add(skuBelow);
        extraTestSkus.add(skuEqual);
        extraTestSkus.add(skuAbove);

        repository.save(new Product(skuBelow, "Below Min Product", new BigDecimal("10.00"), 2, 5));
        repository.save(new Product(skuEqual, "Equal Min Product", new BigDecimal("12.00"), 5, 5));
        repository.save(new Product(skuAbove, "Above Min Product", new BigDecimal("15.00"), 10, 5));

        List<Product> allLowStock = repository.findLowStockProducts();

        // Filter to our test SKUs
        List<Product> testLowStock = allLowStock.stream()
                .filter(p -> p.getSku().equals(skuBelow) || p.getSku().equals(skuEqual) || p.getSku().equals(skuAbove))
                .toList();

        assertEquals(2, testLowStock.size(), "Only products with quantity <= minimum_stock should be returned");
        assertEquals(skuBelow, testLowStock.get(0).getSku(), "Product with lower quantity should be first");
        assertEquals(2, testLowStock.get(0).getQuantity());
        assertEquals(5, testLowStock.get(0).getMinimumStock());

        assertEquals(skuEqual, testLowStock.get(1).getSku(), "Product with equal quantity should be second");
        assertEquals(5, testLowStock.get(1).getQuantity());
        assertEquals(5, testLowStock.get(1).getMinimumStock());
    }

    @Test
    @DisplayName("Should order low-stock products with same quantity by SKU ascending")
    void shouldOrderLowStockProductsWithSameQuantityBySkuAscending() {
        long ts = System.currentTimeMillis();
        String sku1 = "LOW-ORDER-B-" + ts;
        String sku2 = "LOW-ORDER-A-" + ts;

        extraTestSkus.add(sku1);
        extraTestSkus.add(sku2);

        repository.save(new Product(sku1, "Product B", new BigDecimal("10.00"), 3, 5));
        repository.save(new Product(sku2, "Product A", new BigDecimal("10.00"), 3, 5));

        List<Product> allLowStock = repository.findLowStockProducts();
        List<Product> testLowStock = allLowStock.stream()
                .filter(p -> p.getSku().equals(sku1) || p.getSku().equals(sku2))
                .toList();

        assertEquals(2, testLowStock.size());
        assertEquals(sku2, testLowStock.get(0).getSku(), "SKU-A should come before SKU-B");
        assertEquals(sku1, testLowStock.get(1).getSku());
    }
}
