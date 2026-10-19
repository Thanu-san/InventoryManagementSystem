package com.stockflow.service;

import com.stockflow.exception.ValidationException;
import com.stockflow.model.InventorySummary;
import com.stockflow.model.InventoryTransaction;
import com.stockflow.model.InventoryValuationItem;
import com.stockflow.model.InventoryValuationReport;
import com.stockflow.model.PagedResult;
import com.stockflow.model.Product;
import com.stockflow.model.SortDirection;
import com.stockflow.model.SortField;
import com.stockflow.model.TransactionType;
import com.stockflow.repository.InMemoryInventoryTransactionRepository;
import com.stockflow.repository.InMemoryProductRepository;
import com.stockflow.repository.JdbcInventoryTransactionRepository;
import com.stockflow.repository.JdbcProductRepository;
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

    @Test
    @DisplayName("Should throw ValidationException when repository update returns false")
    void shouldFailWhenRepositoryUpdateFails() {
        ProductRepository failingRepo = new InMemoryProductRepository() {
            @Override
            public boolean update(Product product) {
                return false;
            }
        };
        ProductService serviceWithFailingRepo = new ProductService(failingRepo);
        failingRepo.save(new Product("SKU-FAIL", "Fail Item", new BigDecimal("10.00"), 5, 1));

        ValidationException exception = assertThrows(ValidationException.class, () ->
                serviceWithFailingRepo.updateProduct("SKU-FAIL", "New Name", new BigDecimal("12.00"), 2)
        );
        assertTrue(exception.getMessage().contains("Product not found with SKU: SKU-FAIL"));
    }

    @Test
    @DisplayName("Should successfully perform stock in and update product quantity")
    void shouldStockInSuccessfully() {
        productService.createProduct("SKU-IN", "Item In", new BigDecimal("10.00"), 20, 5);

        InventoryTransaction tx = productService.stockIn("SKU-IN", 15);

        assertNotNull(tx);
        assertEquals(TransactionType.STOCK_IN, tx.getType());
        assertEquals(15, tx.getQuantity());

        Product product = productService.findProductBySku("SKU-IN").orElseThrow();
        assertEquals(35, product.getQuantity());
    }

    @Test
    @DisplayName("Should throw ValidationException when stock in SKU is blank")
    void shouldFailStockInWhenSkuIsBlank() {
        assertThrows(ValidationException.class, () -> productService.stockIn("  ", 10));
        assertThrows(ValidationException.class, () -> productService.stockIn(null, 10));
    }

    @Test
    @DisplayName("Should throw ValidationException when stock in quantity is zero or negative")
    void shouldFailStockInWhenQuantityIsInvalid() {
        productService.createProduct("SKU-IN-QTY", "Item", new BigDecimal("10.00"), 20, 5);

        assertThrows(ValidationException.class, () -> productService.stockIn("SKU-IN-QTY", 0));
        assertThrows(ValidationException.class, () -> productService.stockIn("SKU-IN-QTY", -5));
    }

    @Test
    @DisplayName("Should throw ValidationException when stock in product does not exist")
    void shouldFailStockInWhenProductNotFound() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                productService.stockIn("NONEXISTENT", 10));
        assertTrue(ex.getMessage().contains("Product not found with SKU: NONEXISTENT"));
    }

    @Test
    @DisplayName("Should throw ValidationException when stock in causes quantity overflow")
    void shouldFailStockInWhenQuantityCausesOverflow() {
        productService.createProduct("SKU-OVERFLOW", "Item", new BigDecimal("10.00"), 100, 5);

        ValidationException ex = assertThrows(ValidationException.class, () ->
                productService.stockIn("SKU-OVERFLOW", Integer.MAX_VALUE));
        assertTrue(ex.getMessage().contains("maximum capacity"));
    }

    @Test
    @DisplayName("Should successfully perform stock out and update product quantity")
    void shouldStockOutSuccessfully() {
        productService.createProduct("SKU-OUT", "Item Out", new BigDecimal("10.00"), 20, 5);

        InventoryTransaction tx = productService.stockOut("SKU-OUT", 5);

        assertNotNull(tx);
        assertEquals(TransactionType.STOCK_OUT, tx.getType());
        assertEquals(5, tx.getQuantity());

        Product product = productService.findProductBySku("SKU-OUT").orElseThrow();
        assertEquals(15, product.getQuantity());
    }

    @Test
    @DisplayName("Should allow stock out reducing quantity to zero")
    void shouldAllowStockOutToZero() {
        productService.createProduct("SKU-OUT-ZERO", "Item", new BigDecimal("10.00"), 10, 5);

        InventoryTransaction tx = productService.stockOut("SKU-OUT-ZERO", 10);
        assertNotNull(tx);

        Product product = productService.findProductBySku("SKU-OUT-ZERO").orElseThrow();
        assertEquals(0, product.getQuantity());
    }

    @Test
    @DisplayName("Should throw ValidationException when stock out quantity exceeds available stock")
    void shouldFailStockOutWhenInsufficientStock() {
        productService.createProduct("SKU-LOW", "Low Stock Item", new BigDecimal("10.00"), 5, 2);

        ValidationException ex = assertThrows(ValidationException.class, () ->
                productService.stockOut("SKU-LOW", 6));
        assertTrue(ex.getMessage().contains("Insufficient stock"));

        Product product = productService.findProductBySku("SKU-LOW").orElseThrow();
        assertEquals(5, product.getQuantity());
    }

    @Test
    @DisplayName("Should throw ValidationException when stock out SKU is blank")
    void shouldFailStockOutWhenSkuIsBlank() {
        assertThrows(ValidationException.class, () -> productService.stockOut("  ", 5));
        assertThrows(ValidationException.class, () -> productService.stockOut(null, 5));
    }

    @Test
    @DisplayName("Should throw ValidationException when stock out quantity is zero or negative")
    void shouldFailStockOutWhenQuantityIsInvalid() {
        productService.createProduct("SKU-OUT-QTY", "Item", new BigDecimal("10.00"), 20, 5);

        assertThrows(ValidationException.class, () -> productService.stockOut("SKU-OUT-QTY", 0));
        assertThrows(ValidationException.class, () -> productService.stockOut("SKU-OUT-QTY", -3));
    }

    @Test
    @DisplayName("Should throw ValidationException when stock out product does not exist")
    void shouldFailStockOutWhenProductNotFound() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                productService.stockOut("NONEXISTENT", 5));
        assertTrue(ex.getMessage().contains("Product not found with SKU: NONEXISTENT"));
    }

    @Test
    @DisplayName("Should retrieve transaction history for a product")
    void shouldGetTransactionHistorySuccessfully() {
        productService.createProduct("SKU-HIST", "History Item", new BigDecimal("10.00"), 20, 5);

        productService.stockIn("SKU-HIST", 10);
        productService.stockOut("SKU-HIST", 5);

        List<InventoryTransaction> history = productService.getTransactionHistory("SKU-HIST");
        assertEquals(2, history.size());
        assertEquals(TransactionType.STOCK_OUT, history.get(0).getType());
        assertEquals(TransactionType.STOCK_IN, history.get(1).getType());
    }

    @Test
    @DisplayName("Should throw ValidationException when history SKU is blank or product does not exist")
    void shouldFailGetHistoryWhenInvalidSku() {
        assertThrows(ValidationException.class, () -> productService.getTransactionHistory(" "));
        assertThrows(ValidationException.class, () -> productService.getTransactionHistory(null));

        ValidationException ex = assertThrows(ValidationException.class, () ->
                productService.getTransactionHistory("NONEXISTENT"));
        assertTrue(ex.getMessage().contains("Product not found with SKU: NONEXISTENT"));
    }

    @Test
    @DisplayName("Should prevent deleting product when transaction history exists in memory")
    void shouldPreventDeletingProductWhenTransactionHistoryExists() {
        productService.createProduct("SKU-DEL-TX", "Protected Product", new BigDecimal("19.99"), 10, 2);
        productService.stockIn("SKU-DEL-TX", 5);

        ValidationException ex = assertThrows(ValidationException.class, () ->
                productService.deleteProductBySku("SKU-DEL-TX"));
        assertTrue(ex.getMessage().contains("recorded inventory transaction history"));

        assertTrue(productService.findProductBySku("SKU-DEL-TX").isPresent(),
                "Product should not be deleted when it has transaction history");
    }

    @Test
    @DisplayName("Should reject incompatible repository combinations in ProductService")
    void shouldRejectIncompatibleRepositoryCombinations() {
        ProductRepository inMemoryProductRepo = new InMemoryProductRepository();
        ProductRepository jdbcProductRepo = new JdbcProductRepository();
        InMemoryInventoryTransactionRepository inMemoryTxRepo = new InMemoryInventoryTransactionRepository(inMemoryProductRepo);
        JdbcInventoryTransactionRepository jdbcTxRepo = new JdbcInventoryTransactionRepository();

        // 1. JdbcProductRepository + InMemoryInventoryTransactionRepository
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () ->
                new ProductService(jdbcProductRepo, inMemoryTxRepo));
        assertTrue(ex1.getMessage().contains("Unsupported repository combination"));

        // 2. InMemoryProductRepository + JdbcInventoryTransactionRepository
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () ->
                new ProductService(inMemoryProductRepo, jdbcTxRepo));
        assertTrue(ex2.getMessage().contains("Unsupported repository combination"));

        // 3. Null product repository
        assertThrows(IllegalArgumentException.class, () ->
                new ProductService(null, inMemoryTxRepo));
    }

    @Test
    @DisplayName("Should reject constructing InMemoryInventoryTransactionRepository with non-in-memory ProductRepository")
    void shouldRejectConstructingInMemoryTxRepoWithNonInMemoryProductRepo() {
        JdbcProductRepository jdbcProductRepo = new JdbcProductRepository();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new InMemoryInventoryTransactionRepository(jdbcProductRepo));
        assertTrue(ex.getMessage().contains("requires an InMemoryProductRepository"));
    }

    @Test
    @DisplayName("Should permit CRUD but reject stock movements when transaction repository is not configured")
    void shouldPermitCrudButRejectStockMovementsWhenTxRepoNotConfigured() {
        // Construct service with a repository without transaction repository (e.g. mock/stub or JDBC CRUD-only)
        ProductRepository repo = new InMemoryProductRepository();
        ProductService crudOnlyService = new ProductService(repo, null);

        // CRUD operations succeed
        Product p = crudOnlyService.createProduct("SKU-CRUD", "CRUD Item", new BigDecimal("10.00"), 5, 1);
        assertNotNull(p);
        assertTrue(crudOnlyService.findProductBySku("SKU-CRUD").isPresent());

        // Stock movement operations fail explicitly and safely
        IllegalStateException ex1 = assertThrows(IllegalStateException.class, () ->
                crudOnlyService.stockIn("SKU-CRUD", 5));
        assertTrue(ex1.getMessage().contains("Stock movements are not supported"));

        IllegalStateException ex2 = assertThrows(IllegalStateException.class, () ->
                crudOnlyService.stockOut("SKU-CRUD", 2));
        assertTrue(ex2.getMessage().contains("Stock movements are not supported"));

        IllegalStateException ex3 = assertThrows(IllegalStateException.class, () ->
                crudOnlyService.getTransactionHistory("SKU-CRUD"));
        assertTrue(ex3.getMessage().contains("Stock movements are not supported"));
    }

    @Test
    @DisplayName("Should reject mismatched InMemoryProductRepository instances between service and transaction repository")
    void shouldRejectMismatchedInMemoryRepositoryInstances() {
        InMemoryProductRepository serviceRepo = new InMemoryProductRepository();
        InMemoryProductRepository separateTxRepoProductStore = new InMemoryProductRepository();
        InMemoryInventoryTransactionRepository txRepo = new InMemoryInventoryTransactionRepository(separateTxRepoProductStore);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new ProductService(serviceRepo, txRepo));
        assertTrue(ex.getMessage().contains("Mismatched repository instances"));
    }

    @Test
    @DisplayName("Should reject InMemoryInventoryTransactionRepository without product repository in ProductService")
    void shouldRejectInMemoryTxRepoWithoutProductRepositoryInService() {
        InMemoryProductRepository serviceRepo = new InMemoryProductRepository();
        InMemoryInventoryTransactionRepository txRepoWithoutRepo = new InMemoryInventoryTransactionRepository();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new ProductService(serviceRepo, txRepoWithoutRepo));
        assertTrue(ex.getMessage().contains("Mismatched repository instances"));
    }

    @Test
    @DisplayName("Should return low-stock products including quantity below and equal to minimum stock")
    void shouldReturnLowStockProductsCorrectly() {
        // Below minimum stock
        productService.createProduct("SKU-LOW", "Low Item", new BigDecimal("10.00"), 3, 5);
        // Exactly equal to minimum stock
        productService.createProduct("SKU-EQUAL", "Equal Item", new BigDecimal("15.00"), 5, 5);
        // Above minimum stock (must be excluded)
        productService.createProduct("SKU-OK", "Normal Item", new BigDecimal("20.00"), 10, 5);

        List<Product> report = productService.getLowStockProducts();

        assertEquals(2, report.size());
        assertEquals("SKU-LOW", report.get(0).getSku());
        assertEquals("SKU-EQUAL", report.get(1).getSku());

        // Verify product quantities and minimum stock remain completely unchanged
        Product low = productService.findProductBySku("SKU-LOW").orElseThrow();
        assertEquals(3, low.getQuantity());
        assertEquals(5, low.getMinimumStock());

        Product equal = productService.findProductBySku("SKU-EQUAL").orElseThrow();
        assertEquals(5, equal.getQuantity());
        assertEquals(5, equal.getMinimumStock());
    }

    @Test
    @DisplayName("Should return empty list when no products are low-stock")
    void shouldReturnEmptyListWhenNoProductsLowStock() {
        productService.createProduct("SKU-ABOVE1", "Item 1", new BigDecimal("10.00"), 10, 2);
        productService.createProduct("SKU-ABOVE2", "Item 2", new BigDecimal("10.00"), 20, 5);

        List<Product> report = productService.getLowStockProducts();
        assertNotNull(report);
        assertTrue(report.isEmpty());
    }

    @Test
    @DisplayName("Should return empty list when system has no products")
    void shouldReturnEmptyListWhenNoProductsExist() {
        List<Product> report = productService.getLowStockProducts();
        assertNotNull(report);
        assertTrue(report.isEmpty());
    }

    @Test
    @DisplayName("Should return inventory summary with all zeroes when no products exist")
    void shouldReturnZeroSummaryWhenNoProductsExist() {
        InventorySummary summary = productService.getInventorySummary();

        assertNotNull(summary);
        assertEquals(0, summary.getTotalProducts());
        assertEquals(0, summary.getTotalUnits());
        assertEquals(0, summary.getLowStockProducts());
        assertEquals(0, summary.getOutOfStockProducts());
    }

    @Test
    @DisplayName("Should return accurate inventory summary with mixed products")
    void shouldReturnAccurateInventorySummaryWithMixedProducts() {
        productService.createProduct("P-NORMAL", "Normal Item", new BigDecimal("10.00"), 50, 10);
        productService.createProduct("P-LOW", "Low Stock Item", new BigDecimal("20.00"), 5, 10);
        productService.createProduct("P-OUT", "Out of Stock Item", new BigDecimal("30.00"), 0, 5);

        InventorySummary summary = productService.getInventorySummary();

        assertNotNull(summary);
        assertEquals(3, summary.getTotalProducts());
        assertEquals(55, summary.getTotalUnits());
        assertEquals(2, summary.getLowStockProducts()); // P-LOW (5 <= 10) and P-OUT (0 <= 5)
        assertEquals(1, summary.getOutOfStockProducts()); // P-OUT (0)

        // Verify product records in repository are untouched
        Product normal = productService.findProductBySku("P-NORMAL").orElseThrow();
        assertEquals(50, normal.getQuantity());
        Product low = productService.findProductBySku("P-LOW").orElseThrow();
        assertEquals(5, low.getQuantity());
        Product out = productService.findProductBySku("P-OUT").orElseThrow();
        assertEquals(0, out.getQuantity());
    }

    @Test
    @DisplayName("Should return empty valuation report with zero units and 0.00 value when inventory is empty")
    void shouldReturnEmptyValuationReportWhenInventoryIsEmpty() {
        InventoryValuationReport report = productService.getInventoryValuationReport();

        assertNotNull(report);
        assertTrue(report.getItems().isEmpty(), "Items list must be empty for empty inventory");
        assertEquals(0L, report.getTotalQuantity());
        assertEquals(new BigDecimal("0.00"), report.getTotalValue());
    }

    @Test
    @DisplayName("Should calculate valuation correctly for a single product with known price and quantity")
    void shouldCalculateValuationForSingleProduct() {
        productService.createProduct("KB-001", "Keyboard", new BigDecimal("25.00"), 10, 3);

        InventoryValuationReport report = productService.getInventoryValuationReport();

        assertNotNull(report);
        assertEquals(1, report.getItems().size());
        InventoryValuationItem item = report.getItems().get(0);
        assertEquals("KB-001", item.getSku());
        assertEquals("Keyboard", item.getName());
        assertEquals(new BigDecimal("25.00"), item.getUnitPrice());
        assertEquals(10, item.getQuantity());
        assertEquals(new BigDecimal("250.00"), item.getInventoryValue());

        assertEquals(10L, report.getTotalQuantity());
        assertEquals(new BigDecimal("250.00"), report.getTotalValue());
    }

    @Test
    @DisplayName("Should calculate valuation for multiple products and their combined totals")
    void shouldCalculateValuationForMultipleProductsAndCombinedTotal() {
        productService.createProduct("KB-001", "Keyboard", new BigDecimal("25.00"), 10, 2);
        productService.createProduct("MS-001", "Mouse", new BigDecimal("12.50"), 8, 2);

        InventoryValuationReport report = productService.getInventoryValuationReport();

        assertNotNull(report);
        assertEquals(2, report.getItems().size());

        InventoryValuationItem item1 = report.getItems().get(0);
        assertEquals("KB-001", item1.getSku());
        assertEquals("Keyboard", item1.getName());
        assertEquals(new BigDecimal("25.00"), item1.getUnitPrice());
        assertEquals(10, item1.getQuantity());
        assertEquals(new BigDecimal("250.00"), item1.getInventoryValue());

        InventoryValuationItem item2 = report.getItems().get(1);
        assertEquals("MS-001", item2.getSku());
        assertEquals("Mouse", item2.getName());
        assertEquals(new BigDecimal("12.50"), item2.getUnitPrice());
        assertEquals(8, item2.getQuantity());
        assertEquals(new BigDecimal("100.00"), item2.getInventoryValue());

        assertEquals(18L, report.getTotalQuantity(), "Total units should be 10 + 8 = 18");
        assertEquals(new BigDecimal("350.00"), report.getTotalValue(), "Total value should be 250.00 + 100.00 = 350.00");
    }

    @Test
    @DisplayName("Should handle product with zero quantity contributing 0.00 to valuation")
    void shouldHandleProductWithZeroQuantityInValuationReport() {
        productService.createProduct("IN-STOCK", "In Stock Item", new BigDecimal("20.00"), 5, 2);
        productService.createProduct("OUT-STOCK", "Out of Stock Item", new BigDecimal("50.00"), 0, 2);

        InventoryValuationReport report = productService.getInventoryValuationReport();

        assertNotNull(report);
        assertEquals(2, report.getItems().size());

        InventoryValuationItem outItem = report.getItems().stream()
                .filter(i -> "OUT-STOCK".equals(i.getSku()))
                .findFirst()
                .orElseThrow();
        assertEquals(0, outItem.getQuantity());
        assertEquals(new BigDecimal("0.00"), outItem.getInventoryValue());

        assertEquals(5L, report.getTotalQuantity());
        assertEquals(new BigDecimal("100.00"), report.getTotalValue());
    }

    @Test
    @DisplayName("Should maintain exact decimal monetary precision without rounding errors")
    void shouldMaintainDecimalMonetaryPrecisionWithoutRoundingErrors() {
        // 19.99 * 3 = 59.97
        productService.createProduct("DEC-1", "Item 1", new BigDecimal("19.99"), 3, 1);
        // 5.55 * 7 = 38.85
        productService.createProduct("DEC-2", "Item 2", new BigDecimal("5.55"), 7, 1);
        // 0.99 * 10 = 9.90
        productService.createProduct("DEC-3", "Item 3", new BigDecimal("0.99"), 10, 1);

        InventoryValuationReport report = productService.getInventoryValuationReport();

        assertNotNull(report);
        assertEquals(3, report.getItems().size());
        assertEquals(20L, report.getTotalQuantity());
        // 59.97 + 38.85 + 9.90 = 108.72
        assertEquals(new BigDecimal("108.72"), report.getTotalValue());
    }

    @Test
    @DisplayName("Should not mutate products or stock quantities when generating valuation report")
    void shouldNotMutateProductsOrStockQuantitiesDuringValuationReport() {
        productService.createProduct("STATIC-1", "Static Product", new BigDecimal("49.99"), 15, 5);

        // Generate report multiple times
        InventoryValuationReport report1 = productService.getInventoryValuationReport();
        InventoryValuationReport report2 = productService.getInventoryValuationReport();

        assertEquals(report1.getTotalQuantity(), report2.getTotalQuantity());
        assertEquals(report1.getTotalValue(), report2.getTotalValue());

        // Ensure database/in-memory product state is completely preserved
        Product stored = productService.findProductBySku("STATIC-1").orElseThrow();
        assertEquals("STATIC-1", stored.getSku());
        assertEquals("Static Product", stored.getName());
        assertEquals(new BigDecimal("49.99"), stored.getPrice());
        assertEquals(15, stored.getQuantity());
        assertEquals(5, stored.getMinimumStock());
    }

    @Test
    @DisplayName("Should search products by name with partial and case-insensitive matching")
    void shouldSearchProductsByNameWithPartialAndCaseInsensitiveMatching() {
        productService.createProduct("P-KB1", "Mechanical Keyboard", new BigDecimal("89.99"), 10, 2);
        productService.createProduct("P-KB2", "Gaming Keyboard", new BigDecimal("59.99"), 5, 2);
        productService.createProduct("P-MS1", "Optical Mouse", new BigDecimal("25.00"), 8, 2);

        List<Product> results = productService.searchProductsByName("key");
        assertEquals(2, results.size());
        assertEquals("P-KB1", results.get(0).getSku());
        assertEquals("P-KB2", results.get(1).getSku());

        List<Product> upperResults = productService.searchProductsByName("KEYBOARD");
        assertEquals(2, upperResults.size());

        List<Product> mouseResults = productService.searchProductsByName("mouse");
        assertEquals(1, mouseResults.size());
        assertEquals("P-MS1", mouseResults.get(0).getSku());
    }

    @Test
    @DisplayName("Should trim leading and trailing spaces from search query in service")
    void shouldTrimLeadingAndTrailingSpacesFromSearchQueryInService() {
        productService.createProduct("P-KB1", "Mechanical Keyboard", new BigDecimal("89.99"), 10, 2);

        List<Product> results = productService.searchProductsByName("   keyboard   ");
        assertEquals(1, results.size());
        assertEquals("P-KB1", results.get(0).getSku());
    }

    @Test
    @DisplayName("Should throw ValidationException when search term is null, empty, or whitespace")
    void shouldThrowValidationExceptionWhenSearchTermIsBlankOrNull() {
        productService.createProduct("P-KB1", "Mechanical Keyboard", new BigDecimal("89.99"), 10, 2);

        ValidationException ex1 = assertThrows(ValidationException.class, () ->
                productService.searchProductsByName(null));
        assertTrue(ex1.getMessage().contains("cannot be blank"));

        ValidationException ex2 = assertThrows(ValidationException.class, () ->
                productService.searchProductsByName(""));
        assertTrue(ex2.getMessage().contains("cannot be blank"));

        ValidationException ex3 = assertThrows(ValidationException.class, () ->
                productService.searchProductsByName("    "));
        assertTrue(ex3.getMessage().contains("cannot be blank"));
    }

    @Test
    @DisplayName("Should return empty list when no products match search term")
    void shouldReturnEmptyListWhenNoProductsMatchSearchTerm() {
        productService.createProduct("P-KB1", "Mechanical Keyboard", new BigDecimal("89.99"), 10, 2);

        List<Product> results = productService.searchProductsByName("Monitor");
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Should confirm existing findProductBySku functionality is preserved and unaffected")
    void shouldConfirmExistingFindProductBySkuStillWorks() {
        productService.createProduct("FIND-SKU-1", "Desk Lamp", new BigDecimal("19.99"), 20, 5);

        Optional<Product> found = productService.findProductBySku("FIND-SKU-1");
        assertTrue(found.isPresent());
        assertEquals("Desk Lamp", found.get().getName());

        Optional<Product> notFound = productService.findProductBySku("NON-EXISTENT");
        assertTrue(notFound.isEmpty());
    }

    @Test
    @DisplayName("Should ensure search by name does not mutate product data")
    void shouldNotMutateProductDataDuringSearch() {
        productService.createProduct("MUT-1", "Webcam HD", new BigDecimal("45.00"), 12, 3);

        List<Product> results = productService.searchProductsByName("webcam");
        assertEquals(1, results.size());

        Product p = productService.findProductBySku("MUT-1").orElseThrow();
        assertEquals("MUT-1", p.getSku());
        assertEquals("Webcam HD", p.getName());
        assertEquals(new BigDecimal("45.00"), p.getPrice());
        assertEquals(12, p.getQuantity());
        assertEquals(3, p.getMinimumStock());
    }

    @Test
    @DisplayName("Should throw ValidationException when page number is less than 1")
    void shouldThrowValidationExceptionWhenPageIsLessThanOne() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                productService.getProductsPaged(0, 5, SortField.SKU, SortDirection.ASC));
        assertTrue(ex.getMessage().contains("Page number must be greater than or equal to 1"));

        assertThrows(ValidationException.class, () ->
                productService.getProductsPaged(-1, 5, SortField.SKU, SortDirection.ASC));
    }

    @Test
    @DisplayName("Should throw ValidationException when page size is less than 1")
    void shouldThrowValidationExceptionWhenPageSizeIsLessThanOne() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                productService.getProductsPaged(1, 0, SortField.SKU, SortDirection.ASC));
        assertTrue(ex.getMessage().contains("Page size must be greater than or equal to 1"));

        assertThrows(ValidationException.class, () ->
                productService.getProductsPaged(1, -5, SortField.SKU, SortDirection.ASC));
    }

    @Test
    @DisplayName("Should throw ValidationException when sort field or direction is null")
    void shouldThrowValidationExceptionWhenSortFieldOrDirectionIsNull() {
        ValidationException exField = assertThrows(ValidationException.class, () ->
                productService.getProductsPaged(1, 5, null, SortDirection.ASC));
        assertTrue(exField.getMessage().contains("Sort field cannot be null"));

        ValidationException exDir = assertThrows(ValidationException.class, () ->
                productService.getProductsPaged(1, 5, SortField.SKU, null));
        assertTrue(exDir.getMessage().contains("Sort direction cannot be null"));
    }

    @Test
    @DisplayName("Should return paged and sorted products successfully via service")
    void shouldReturnPagedAndSortedProductsSuccessfully() {
        productService.createProduct("P-Z", "Product Z", new BigDecimal("30.00"), 10, 1);
        productService.createProduct("P-A", "Product A", new BigDecimal("10.00"), 5, 1);
        productService.createProduct("P-M", "Product M", new BigDecimal("20.00"), 15, 1);

        PagedResult<Product> page1 = productService.getProductsPaged(1, 2, SortField.PRICE, SortDirection.ASC);
        assertEquals(2, page1.getContent().size());
        assertEquals("P-A", page1.getContent().get(0).getSku());
        assertEquals("P-M", page1.getContent().get(1).getSku());
        assertEquals(3L, page1.getTotalElements());
        assertEquals(2, page1.getTotalPages());
        assertTrue(page1.hasNext());
        assertFalse(page1.hasPrevious());

        PagedResult<Product> page2 = productService.getProductsPaged(2, 2, SortField.PRICE, SortDirection.ASC);
        assertEquals(1, page2.getContent().size());
        assertEquals("P-Z", page2.getContent().get(0).getSku());
        assertFalse(page2.hasNext());
        assertTrue(page2.hasPrevious());
    }

    @Test
    @DisplayName("Should accurately report product count from service")
    void shouldAccuratelyReportProductCountFromService() {
        assertEquals(0L, productService.getProductCount());

        productService.createProduct("COUNT-1", "Item 1", new BigDecimal("10.00"), 5, 1);
        productService.createProduct("COUNT-2", "Item 2", new BigDecimal("20.00"), 10, 2);

        assertEquals(2L, productService.getProductCount());
    }

    @Test
    @DisplayName("Should ensure paged browsing does not mutate product data")
    void shouldEnsurePagedBrowsingDoesNotMutateProductData() {
        productService.createProduct("NO-MUT-1", "Safe Product", new BigDecimal("35.00"), 8, 2);

        PagedResult<Product> result = productService.getProductsPaged(1, 10, SortField.NAME, SortDirection.ASC);
        assertEquals(1, result.getContent().size());

        Product stored = productService.findProductBySku("NO-MUT-1").orElseThrow();
        assertEquals("NO-MUT-1", stored.getSku());
        assertEquals("Safe Product", stored.getName());
        assertEquals(new BigDecimal("35.00"), stored.getPrice());
        assertEquals(8, stored.getQuantity());
        assertEquals(2, stored.getMinimumStock());
    }

    @Test
    @DisplayName("Should ensure failed update validation does not mutate existing product data")
    void shouldEnsureFailedUpdateValidationDoesNotMutateExistingProductData() {
        productService.createProduct("IMMUT-1", "Original Name", new BigDecimal("50.00"), 10, 2);

        // 1. Blank name
        assertThrows(ValidationException.class, () ->
                productService.updateProduct("IMMUT-1", "   ", new BigDecimal("60.00"), 3));

        // 2. Negative price
        assertThrows(ValidationException.class, () ->
                productService.updateProduct("IMMUT-1", "Changed Name", new BigDecimal("-10.00"), 3));

        // 3. Negative minimum stock
        assertThrows(ValidationException.class, () ->
                productService.updateProduct("IMMUT-1", "Changed Name", new BigDecimal("60.00"), -1));

        // Verify product remains completely unchanged
        Product p = productService.findProductBySku("IMMUT-1").orElseThrow();
        assertEquals("Original Name", p.getName());
        assertEquals(new BigDecimal("50.00"), p.getPrice());
        assertEquals(10, p.getQuantity());
        assertEquals(2, p.getMinimumStock());
    }

    @Test
    @DisplayName("Should ensure failed stock movement validation does not mutate product quantity")
    void shouldEnsureFailedStockMovementValidationDoesNotMutateProductQuantity() {
        productService.createProduct("IMMUT-STK", "Stock Item", new BigDecimal("20.00"), 10, 2);

        // 1. Invalid stock in quantity
        assertThrows(ValidationException.class, () -> productService.stockIn("IMMUT-STK", 0));
        assertThrows(ValidationException.class, () -> productService.stockIn("IMMUT-STK", -5));

        // 2. Invalid stock out quantity
        assertThrows(ValidationException.class, () -> productService.stockOut("IMMUT-STK", 0));
        assertThrows(ValidationException.class, () -> productService.stockOut("IMMUT-STK", -3));

        // 3. Stock out exceeding quantity
        assertThrows(ValidationException.class, () -> productService.stockOut("IMMUT-STK", 15));

        // Verify quantity remains unchanged at 10
        Product p = productService.findProductBySku("IMMUT-STK").orElseThrow();
        assertEquals(10, p.getQuantity());
    }

    @Test
    @DisplayName("Should ensure product with transaction history is not deleted when delete fails")
    void shouldEnsureProductWithTransactionHistoryIsNotDeleted() {
        productService.createProduct("DEL-PROT", "Protected Item", new BigDecimal("100.00"), 5, 1);
        productService.stockIn("DEL-PROT", 3);

        // Attempt deletion
        ValidationException ex = assertThrows(ValidationException.class, () ->
                productService.deleteProductBySku("DEL-PROT"));
        assertTrue(ex.getMessage().contains("has recorded inventory transaction history"));

        // Verify product is still present
        assertTrue(productService.findProductBySku("DEL-PROT").isPresent());
    }
}
