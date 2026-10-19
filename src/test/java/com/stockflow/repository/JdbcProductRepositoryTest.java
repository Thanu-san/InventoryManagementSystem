package com.stockflow.repository;

import com.stockflow.database.DatabaseConnection;
import com.stockflow.exception.ValidationException;
import com.stockflow.model.InventorySummary;
import com.stockflow.model.InventoryValuationItem;
import com.stockflow.model.InventoryValuationReport;
import com.stockflow.model.PagedResult;
import com.stockflow.model.Product;
import com.stockflow.model.SortDirection;
import com.stockflow.model.SortField;
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

    @Test
    @DisplayName("Should execute single aggregate query in PostgreSQL and return accurate InventorySummary metrics")
    void shouldReturnAccurateInventorySummaryFromPostgreSql() {
        InventorySummary before = repository.getInventorySummary();
        assertNotNull(before);
        assertTrue(before.getTotalProducts() >= 0);
        assertTrue(before.getTotalUnits() >= 0);
        assertTrue(before.getLowStockProducts() >= 0);
        assertTrue(before.getOutOfStockProducts() >= 0);

        long ts = System.currentTimeMillis();
        String skuNormal = "SUM-NORM-" + ts;
        String skuLow = "SUM-LOW-" + ts;
        String skuOut = "SUM-OUT-" + ts;

        extraTestSkus.add(skuNormal);
        extraTestSkus.add(skuLow);
        extraTestSkus.add(skuOut);

        // Product 1: normal (qty 40, min 10)
        repository.save(new Product(skuNormal, "Summary Normal", new BigDecimal("10.00"), 40, 10));
        // Product 2: low-stock (qty 5, min 10) -> 5 <= 10
        repository.save(new Product(skuLow, "Summary Low", new BigDecimal("20.00"), 5, 10));
        // Product 3: out-of-stock and low-stock (qty 0, min 10) -> 0 <= 10 and 0 == 0
        repository.save(new Product(skuOut, "Summary Out", new BigDecimal("30.00"), 0, 10));

        InventorySummary after = repository.getInventorySummary();
        assertNotNull(after);

        assertEquals(before.getTotalProducts() + 3, after.getTotalProducts(), "Total products should increase by 3");
        assertEquals(before.getTotalUnits() + 45, after.getTotalUnits(), "Total units should increase by 40 + 5 + 0 = 45");
        assertEquals(before.getLowStockProducts() + 2, after.getLowStockProducts(), "Low-stock products should increase by 2 (skuLow and skuOut)");
        assertEquals(before.getOutOfStockProducts() + 1, after.getOutOfStockProducts(), "Out-of-stock products should increase by 1 (skuOut)");
    }

    @Test
    @DisplayName("Should retrieve products from PostgreSQL and calculate accurate InventoryValuationReport")
    void shouldCalculateAccurateInventoryValuationReportFromPostgreSql() {
        long ts = System.currentTimeMillis();
        String sku1 = "VAL-1-" + ts;
        String sku2 = "VAL-2-" + ts;
        String skuZero = "VAL-ZERO-" + ts;

        extraTestSkus.add(sku1);
        extraTestSkus.add(sku2);
        extraTestSkus.add(skuZero);

        // Product 1: 25.00 * 10 = 250.00
        repository.save(new Product(sku1, "Valuation Item 1", new BigDecimal("25.00"), 10, 2));
        // Product 2: 12.50 * 8 = 100.00
        repository.save(new Product(sku2, "Valuation Item 2", new BigDecimal("12.50"), 8, 2));
        // Product 3: 50.00 * 0 = 0.00
        repository.save(new Product(skuZero, "Zero Qty Item", new BigDecimal("50.00"), 0, 2));

        ProductService service = new ProductService(repository);
        InventoryValuationReport report = service.getInventoryValuationReport();

        assertNotNull(report);

        // Verify valuations for our test products retrieved from PostgreSQL
        InventoryValuationItem item1 = report.getItems().stream()
                .filter(i -> sku1.equals(i.getSku()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Item 1 not found in report"));
        assertEquals("Valuation Item 1", item1.getName());
        assertEquals(0, new BigDecimal("25.00").compareTo(item1.getUnitPrice()));
        assertEquals(10, item1.getQuantity());
        assertEquals(0, new BigDecimal("250.00").compareTo(item1.getInventoryValue()));

        InventoryValuationItem item2 = report.getItems().stream()
                .filter(i -> sku2.equals(i.getSku()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Item 2 not found in report"));
        assertEquals("Valuation Item 2", item2.getName());
        assertEquals(0, new BigDecimal("12.50").compareTo(item2.getUnitPrice()));
        assertEquals(8, item2.getQuantity());
        assertEquals(0, new BigDecimal("100.00").compareTo(item2.getInventoryValue()));

        InventoryValuationItem itemZero = report.getItems().stream()
                .filter(i -> skuZero.equals(i.getSku()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Zero Qty Item not found in report"));
        assertEquals("Zero Qty Item", itemZero.getName());
        assertEquals(0, new BigDecimal("50.00").compareTo(itemZero.getUnitPrice()));
        assertEquals(0, itemZero.getQuantity());
        assertEquals(0, new BigDecimal("0.00").compareTo(itemZero.getInventoryValue()));

        // Confirm database records are not mutated
        Product retrieved1 = repository.findBySku(sku1).orElseThrow();
        assertEquals(10, retrieved1.getQuantity());
        assertEquals(0, new BigDecimal("25.00").compareTo(retrieved1.getPrice()));

        Product retrievedZero = repository.findBySku(skuZero).orElseThrow();
        assertEquals(0, retrievedZero.getQuantity());
        assertEquals(0, new BigDecimal("50.00").compareTo(retrievedZero.getPrice()));
    }

    @Test
    @DisplayName("Should search products by name in PostgreSQL case-insensitively and ordered by SKU ascending")
    void shouldSearchProductsByNameInPostgreSqlCaseInsensitiveAndOrderedBySku() {
        long ts = System.currentTimeMillis();
        String sku1 = "SEARCH-B-" + ts;
        String sku2 = "SEARCH-A-" + ts;
        String sku3 = "SEARCH-C-" + ts;

        extraTestSkus.add(sku1);
        extraTestSkus.add(sku2);
        extraTestSkus.add(sku3);

        repository.save(new Product(sku1, "Mechanical Gaming Keyboard", new BigDecimal("79.99"), 10, 2));
        repository.save(new Product(sku2, "Ergonomic Office Keyboard", new BigDecimal("59.99"), 5, 2));
        repository.save(new Product(sku3, "Optical Gaming Mouse", new BigDecimal("29.99"), 8, 2));

        // Partial and case-insensitive search
        List<Product> results = repository.searchByName("keyboard").stream()
                .filter(p -> extraTestSkus.contains(p.getSku()))
                .toList();

        assertEquals(2, results.size());
        assertEquals(sku2, results.get(0).getSku(), "SEARCH-A should precede SEARCH-B");
        assertEquals("Ergonomic Office Keyboard", results.get(0).getName());
        assertEquals(sku1, results.get(1).getSku(), "SEARCH-B should follow SEARCH-A");
        assertEquals("Mechanical Gaming Keyboard", results.get(1).getName());

        // Uppercase query
        List<Product> upperResults = repository.searchByName("KEY").stream()
                .filter(p -> extraTestSkus.contains(p.getSku()))
                .toList();
        assertEquals(2, upperResults.size());
    }

    @Test
    @DisplayName("Should handle SQL wildcard characters literally without pattern expansion in PostgreSQL")
    void shouldHandleSqlWildcardCharactersLiterallyWithoutPatternExpansion() {
        long ts = System.currentTimeMillis();
        String skuPerc = "WILD-PERC-" + ts;
        String skuOther = "WILD-OTHER-" + ts;
        String skuUnder = "WILD-UNDER-" + ts;
        String skuLetter = "WILD-LETTER-" + ts;
        String skuExcl = "WILD-EXCL-" + ts;

        extraTestSkus.add(skuPerc);
        extraTestSkus.add(skuOther);
        extraTestSkus.add(skuUnder);
        extraTestSkus.add(skuLetter);
        extraTestSkus.add(skuExcl);

        repository.save(new Product(skuPerc, "100% Wool Sweater", new BigDecimal("99.99"), 5, 1));
        repository.save(new Product(skuOther, "1000 Watt Blender", new BigDecimal("89.99"), 3, 1));
        repository.save(new Product(skuUnder, "Cable_USB Cord", new BigDecimal("9.99"), 20, 5));
        repository.save(new Product(skuLetter, "CableXUSB Cord", new BigDecimal("11.99"), 15, 5));
        repository.save(new Product(skuExcl, "Super!Sale Monitor", new BigDecimal("199.99"), 2, 1));

        // Literal '%' must match only the product containing '%', not '1000'
        List<Product> percResults = repository.searchByName("100%").stream()
                .filter(p -> extraTestSkus.contains(p.getSku()))
                .toList();
        assertEquals(1, percResults.size());
        assertEquals(skuPerc, percResults.get(0).getSku());

        // Literal '_' must match only 'Cable_USB', not 'CableXUSB'
        List<Product> underResults = repository.searchByName("Cable_USB").stream()
                .filter(p -> extraTestSkus.contains(p.getSku()))
                .toList();
        assertEquals(1, underResults.size());
        assertEquals(skuUnder, underResults.get(0).getSku());

        // Literal '!' must match 'Super!Sale'
        List<Product> exclResults = repository.searchByName("Super!Sale").stream()
                .filter(p -> extraTestSkus.contains(p.getSku()))
                .toList();
        assertEquals(1, exclResults.size());
        assertEquals(skuExcl, exclResults.get(0).getSku());
    }

    @Test
    @DisplayName("Should return empty list when no products match search query in PostgreSQL")
    void shouldReturnEmptyListWhenNoProductsMatchInPostgreSql() {
        List<Product> results = repository.searchByName("NonExistentProductNameXYZ999");
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Should return empty list for null or whitespace query in PostgreSQL")
    void shouldReturnEmptyListForBlankOrNullQueryInPostgreSql() {
        assertTrue(repository.searchByName(null).isEmpty());
        assertTrue(repository.searchByName("").isEmpty());
        assertTrue(repository.searchByName("    ").isEmpty());
    }

    @Test
    @DisplayName("Should accurately count products in PostgreSQL")
    void shouldAccuratelyCountProductsInPostgreSql() {
        long initialCount = repository.count();
        assertTrue(initialCount >= 0, "Initial count should be non-negative");

        long ts = System.currentTimeMillis();
        String sku1 = "COUNT-A-" + ts;
        String sku2 = "COUNT-B-" + ts;
        extraTestSkus.add(sku1);
        extraTestSkus.add(sku2);

        repository.save(new Product(sku1, "Count Product 1", new BigDecimal("10.00"), 5, 1));
        assertEquals(initialCount + 1, repository.count());

        repository.save(new Product(sku2, "Count Product 2", new BigDecimal("20.00"), 10, 2));
        assertEquals(initialCount + 2, repository.count());
    }

    @Test
    @DisplayName("Should sort products by SKU ascending and descending in PostgreSQL")
    void shouldSortProductsBySkuAscAndDescInPostgreSql() {
        long ts = System.currentTimeMillis();
        String sku1 = "SORT-SKU-A-" + ts;
        String sku2 = "SORT-SKU-B-" + ts;
        String sku3 = "SORT-SKU-C-" + ts;
        extraTestSkus.add(sku1);
        extraTestSkus.add(sku2);
        extraTestSkus.add(sku3);

        repository.save(new Product(sku1, "Product A", new BigDecimal("10.00"), 5, 1));
        repository.save(new Product(sku2, "Product B", new BigDecimal("20.00"), 10, 2));
        repository.save(new Product(sku3, "Product C", new BigDecimal("30.00"), 15, 3));

        int total = (int) repository.count();

        // SKU ASC
        PagedResult<Product> ascResult = repository.findAll(1, total, SortField.SKU, SortDirection.ASC);
        List<Product> ascFiltered = ascResult.getContent().stream()
                .filter(p -> extraTestSkus.contains(p.getSku()))
                .toList();
        assertEquals(3, ascFiltered.size());
        assertEquals(sku1, ascFiltered.get(0).getSku());
        assertEquals(sku2, ascFiltered.get(1).getSku());
        assertEquals(sku3, ascFiltered.get(2).getSku());

        // SKU DESC
        PagedResult<Product> descResult = repository.findAll(1, total, SortField.SKU, SortDirection.DESC);
        List<Product> descFiltered = descResult.getContent().stream()
                .filter(p -> extraTestSkus.contains(p.getSku()))
                .toList();
        assertEquals(3, descFiltered.size());
        assertEquals(sku3, descFiltered.get(0).getSku());
        assertEquals(sku2, descFiltered.get(1).getSku());
        assertEquals(sku1, descFiltered.get(2).getSku());
    }

    @Test
    @DisplayName("Should sort products by Name with SKU tie-breaker in PostgreSQL")
    void shouldSortProductsByNameWithSkuTieBreakerInPostgreSql() {
        long ts = System.currentTimeMillis();
        String skuTie1 = "SORT-NAME-TIE-A-" + ts;
        String skuTie2 = "SORT-NAME-TIE-B-" + ts;
        String skuOther = "SORT-NAME-OTHER-" + ts;
        extraTestSkus.add(skuTie1);
        extraTestSkus.add(skuTie2);
        extraTestSkus.add(skuOther);

        // Same name for tie-breaker check
        repository.save(new Product(skuTie2, "Alpha Product", new BigDecimal("10.00"), 5, 1));
        repository.save(new Product(skuTie1, "Alpha Product", new BigDecimal("20.00"), 10, 2));
        repository.save(new Product(skuOther, "Beta Product", new BigDecimal("30.00"), 15, 3));

        int total = (int) repository.count();

        // NAME ASC: Alpha Product (TIE-A then TIE-B), then Beta Product
        PagedResult<Product> ascResult = repository.findAll(1, total, SortField.NAME, SortDirection.ASC);
        List<Product> ascFiltered = ascResult.getContent().stream()
                .filter(p -> extraTestSkus.contains(p.getSku()))
                .toList();
        assertEquals(3, ascFiltered.size());
        assertEquals(skuTie1, ascFiltered.get(0).getSku());
        assertEquals(skuTie2, ascFiltered.get(1).getSku());
        assertEquals(skuOther, ascFiltered.get(2).getSku());

        // NAME DESC: Beta Product, then Alpha Product (TIE-A then TIE-B tie-breaker)
        PagedResult<Product> descResult = repository.findAll(1, total, SortField.NAME, SortDirection.DESC);
        List<Product> descFiltered = descResult.getContent().stream()
                .filter(p -> extraTestSkus.contains(p.getSku()))
                .toList();
        assertEquals(3, descFiltered.size());
        assertEquals(skuOther, descFiltered.get(0).getSku());
        assertEquals(skuTie1, descFiltered.get(1).getSku());
        assertEquals(skuTie2, descFiltered.get(2).getSku());
    }

    @Test
    @DisplayName("Should sort products by Price with SKU tie-breaker in PostgreSQL")
    void shouldSortProductsByPriceWithSkuTieBreakerInPostgreSql() {
        long ts = System.currentTimeMillis();
        String skuLow = "SORT-PRICE-LOW-" + ts;
        String skuTie1 = "SORT-PRICE-TIE-A-" + ts;
        String skuTie2 = "SORT-PRICE-TIE-B-" + ts;
        extraTestSkus.add(skuLow);
        extraTestSkus.add(skuTie1);
        extraTestSkus.add(skuTie2);

        repository.save(new Product(skuLow, "Low Price", new BigDecimal("15.00"), 5, 1));
        repository.save(new Product(skuTie2, "Tie Price B", new BigDecimal("55.00"), 10, 2));
        repository.save(new Product(skuTie1, "Tie Price A", new BigDecimal("55.00"), 15, 3));

        int total = (int) repository.count();

        // PRICE ASC
        PagedResult<Product> ascResult = repository.findAll(1, total, SortField.PRICE, SortDirection.ASC);
        List<Product> ascFiltered = ascResult.getContent().stream()
                .filter(p -> extraTestSkus.contains(p.getSku()))
                .toList();
        assertEquals(3, ascFiltered.size());
        assertEquals(skuLow, ascFiltered.get(0).getSku());
        assertEquals(skuTie1, ascFiltered.get(1).getSku());
        assertEquals(skuTie2, ascFiltered.get(2).getSku());

        // PRICE DESC
        PagedResult<Product> descResult = repository.findAll(1, total, SortField.PRICE, SortDirection.DESC);
        List<Product> descFiltered = descResult.getContent().stream()
                .filter(p -> extraTestSkus.contains(p.getSku()))
                .toList();
        assertEquals(3, descFiltered.size());
        assertEquals(skuTie1, descFiltered.get(0).getSku());
        assertEquals(skuTie2, descFiltered.get(1).getSku());
        assertEquals(skuLow, descFiltered.get(2).getSku());
    }

    @Test
    @DisplayName("Should sort products by Quantity with SKU tie-breaker in PostgreSQL")
    void shouldSortProductsByQuantityWithSkuTieBreakerInPostgreSql() {
        long ts = System.currentTimeMillis();
        String skuLow = "SORT-QTY-LOW-" + ts;
        String skuTie1 = "SORT-QTY-TIE-A-" + ts;
        String skuTie2 = "SORT-QTY-TIE-B-" + ts;
        extraTestSkus.add(skuLow);
        extraTestSkus.add(skuTie1);
        extraTestSkus.add(skuTie2);

        repository.save(new Product(skuLow, "Low Qty", new BigDecimal("15.00"), 2, 1));
        repository.save(new Product(skuTie2, "Tie Qty B", new BigDecimal("25.00"), 20, 2));
        repository.save(new Product(skuTie1, "Tie Qty A", new BigDecimal("35.00"), 20, 3));

        int total = (int) repository.count();

        // QUANTITY ASC
        PagedResult<Product> ascResult = repository.findAll(1, total, SortField.QUANTITY, SortDirection.ASC);
        List<Product> ascFiltered = ascResult.getContent().stream()
                .filter(p -> extraTestSkus.contains(p.getSku()))
                .toList();
        assertEquals(3, ascFiltered.size());
        assertEquals(skuLow, ascFiltered.get(0).getSku());
        assertEquals(skuTie1, ascFiltered.get(1).getSku());
        assertEquals(skuTie2, ascFiltered.get(2).getSku());

        // QUANTITY DESC
        PagedResult<Product> descResult = repository.findAll(1, total, SortField.QUANTITY, SortDirection.DESC);
        List<Product> descFiltered = descResult.getContent().stream()
                .filter(p -> extraTestSkus.contains(p.getSku()))
                .toList();
        assertEquals(3, descFiltered.size());
        assertEquals(skuTie1, descFiltered.get(0).getSku());
        assertEquals(skuTie2, descFiltered.get(1).getSku());
        assertEquals(skuLow, descFiltered.get(2).getSku());
    }

    @Test
    @DisplayName("Should paginate products correctly in PostgreSQL")
    void shouldPaginateProductsCorrectlyInPostgreSql() {
        long ts = System.currentTimeMillis();
        String sku1 = "PAGE-A-" + ts;
        String sku2 = "PAGE-B-" + ts;
        String sku3 = "PAGE-C-" + ts;
        extraTestSkus.add(sku1);
        extraTestSkus.add(sku2);
        extraTestSkus.add(sku3);

        repository.save(new Product(sku1, "Page 1 Item", new BigDecimal("10.00"), 5, 1));
        repository.save(new Product(sku2, "Page 2 Item", new BigDecimal("20.00"), 10, 2));
        repository.save(new Product(sku3, "Page 3 Item", new BigDecimal("30.00"), 15, 3));

        PagedResult<Product> page1 = repository.findAll(1, 2, SortField.SKU, SortDirection.ASC);
        PagedResult<Product> page2 = repository.findAll(2, 2, SortField.SKU, SortDirection.ASC);

        assertEquals(1, page1.getPageNumber());
        assertEquals(2, page1.getPageSize());
        assertTrue(page1.getTotalElements() >= 3);
        assertEquals(2, page1.getContent().size());
        assertTrue(page1.isFirst());
        assertTrue(page1.hasNext());

        assertEquals(2, page2.getPageNumber());
        assertEquals(2, page2.getPageSize());
        assertFalse(page2.getContent().isEmpty());
        assertTrue(page2.hasPrevious());

        // Products on page 1 and page 2 must not overlap
        for (Product p1 : page1.getContent()) {
            assertFalse(page2.getContent().stream().anyMatch(p2 -> p2.getSku().equals(p1.getSku())),
                    "Page 1 and Page 2 should not share items");
        }

        // Sorting continuity across pages
        Product lastOnP1 = page1.getContent().get(page1.getContent().size() - 1);
        Product firstOnP2 = page2.getContent().get(0);
        assertTrue(lastOnP1.getSku().compareTo(firstOnP2.getSku()) < 0,
                "Last item on page 1 must precede first item on page 2");
    }

    @Test
    @DisplayName("Should return empty page when offset exceeds total elements in PostgreSQL")
    void shouldReturnEmptyPageWhenOffsetExceedsTotalElementsInPostgreSql() {
        long total = repository.count();
        int farPage = (int) (total / 5 + 10);

        PagedResult<Product> result = repository.findAll(farPage, 5, SortField.SKU, SortDirection.ASC);
        assertNotNull(result);
        assertTrue(result.getContent().isEmpty());
        assertEquals(farPage, result.getPageNumber());
        assertEquals(5, result.getPageSize());
        assertEquals(total, result.getTotalElements());
    }

    @Test
    @DisplayName("Should handle large offset without integer overflow in PostgreSQL")
    void shouldHandleLargeOffsetWithoutIntegerOverflowInPostgreSql() {
        PagedResult<Product> result = repository.findAll(Integer.MAX_VALUE, 100, SortField.SKU, SortDirection.ASC);
        assertNotNull(result);
        assertTrue(result.getContent().isEmpty());
        assertEquals(Integer.MAX_VALUE, result.getPageNumber());
    }

    @Test
    @DisplayName("Should reject invalid pagination and sorting arguments in PostgreSQL")
    void shouldRejectInvalidPaginationAndSortingArgumentsInPostgreSql() {
        assertThrows(IllegalArgumentException.class, () ->
                repository.findAll(0, 10, SortField.SKU, SortDirection.ASC));

        assertThrows(IllegalArgumentException.class, () ->
                repository.findAll(1, 0, SortField.SKU, SortDirection.ASC));

        assertThrows(IllegalArgumentException.class, () ->
                repository.findAll(1, 10, null, SortDirection.ASC));

        assertThrows(IllegalArgumentException.class, () ->
                repository.findAll(1, 10, SortField.SKU, null));
    }

    @Test
    @DisplayName("Should throw ValidationException when duplicate SKU is inserted directly into PostgreSQL")
    void shouldThrowValidationExceptionWhenDuplicateSkuInsertedDirectlyIntoPostgreSql() {
        long ts = System.currentTimeMillis();
        String dupSku = "DUP-DIRECT-" + ts;
        extraTestSkus.add(dupSku);

        Product first = new Product(dupSku, "First Product", new BigDecimal("10.00"), 5, 1);
        repository.save(first);

        Product duplicate = new Product(dupSku, "Duplicate Product", new BigDecimal("20.00"), 10, 2);
        ValidationException ex = assertThrows(ValidationException.class, () ->
                repository.save(duplicate));
        assertTrue(ex.getMessage().contains("already exists"));
    }

    @Test
    @DisplayName("Should reject save when product is null in PostgreSQL repository")
    void shouldRejectSaveWhenProductIsNullInPostgreSql() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                repository.save(null));
        assertTrue(ex.getMessage().contains("Product and its SKU cannot be null or blank"));
    }

    @Test
    @DisplayName("Should return false when updating with null product in PostgreSQL")
    void shouldReturnFalseWhenUpdatingWithNullProductInPostgreSql() {
        assertFalse(repository.update(null));
    }

    @Test
    @DisplayName("Should return false when deleting with null or blank SKU in PostgreSQL")
    void shouldReturnFalseWhenDeletingWithNullOrBlankSkuInPostgreSql() {
        assertFalse(repository.deleteBySku(null));
        assertFalse(repository.deleteBySku("   "));
        assertFalse(repository.deleteBySku(""));
    }
}
