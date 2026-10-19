package com.stockflow.repository;

import com.stockflow.model.InventorySummary;
import com.stockflow.model.PagedResult;
import com.stockflow.model.Product;
import com.stockflow.model.SortDirection;
import com.stockflow.model.SortField;
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

    @Test
    @DisplayName("Should return all zeroes when repository is empty")
    void shouldReturnAllZeroesWhenRepositoryIsEmpty() {
        InventorySummary summary = repository.getInventorySummary();

        assertNotNull(summary);
        assertEquals(0, summary.getTotalProducts());
        assertEquals(0, summary.getTotalUnits());
        assertEquals(0, summary.getLowStockProducts());
        assertEquals(0, summary.getOutOfStockProducts());
    }

    @Test
    @DisplayName("Should accurately calculate summary metrics with mixed products")
    void shouldCalculateSummaryMetricsWithMixedProducts() {
        // Normal product: qty > minStock
        repository.save(new Product("SKU-NORM", "Normal Item", new BigDecimal("10.00"), 20, 5));
        // Low-stock product: qty <= minStock, but qty > 0
        repository.save(new Product("SKU-LOW", "Low Item", new BigDecimal("15.00"), 3, 5));
        // Out-of-stock product: qty == 0, minStock == 5 (counts as BOTH low-stock and out-of-stock)
        repository.save(new Product("SKU-OUT", "Out Item", new BigDecimal("20.00"), 0, 5));
        // Another normal product
        repository.save(new Product("SKU-NORM2", "Normal Item 2", new BigDecimal("25.00"), 10, 5));

        InventorySummary summary = repository.getInventorySummary();

        assertEquals(4, summary.getTotalProducts(), "Total products should count all 4 records");
        assertEquals(33, summary.getTotalUnits(), "Total units should be 20 + 3 + 0 + 10 = 33");
        assertEquals(2, summary.getLowStockProducts(), "Low-stock should count SKU-LOW (3<=5) and SKU-OUT (0<=5)");
        assertEquals(1, summary.getOutOfStockProducts(), "Out-of-stock should count SKU-OUT (0)");
    }

    @Test
    @DisplayName("Should correctly handle out-of-stock product with zero minimum stock")
    void shouldHandleOutOfStockWithZeroMinStock() {
        // Out of stock, min stock 0: 0 <= 0 is true, so counts as both low-stock and out-of-stock
        repository.save(new Product("SKU-ZERO", "Zero Item", new BigDecimal("5.00"), 0, 0));

        InventorySummary summary = repository.getInventorySummary();

        assertEquals(1, summary.getTotalProducts());
        assertEquals(0, summary.getTotalUnits());
        assertEquals(1, summary.getLowStockProducts());
        assertEquals(1, summary.getOutOfStockProducts());
    }

    @Test
    @DisplayName("Should find products by exact and partial name case-insensitively")
    void shouldFindProductsByExactAndPartialNameCaseInsensitive() {
        repository.save(new Product("KB-001", "Mechanical Keyboard", new BigDecimal("79.99"), 10, 2));
        repository.save(new Product("KB-002", "Wireless Keyboard", new BigDecimal("49.99"), 5, 2));
        repository.save(new Product("MS-001", "Gaming Mouse", new BigDecimal("29.99"), 8, 2));

        // Partial and case-insensitive
        List<Product> keyResults = repository.searchByName("key");
        assertEquals(2, keyResults.size());
        assertEquals("KB-001", keyResults.get(0).getSku());
        assertEquals("KB-002", keyResults.get(1).getSku());

        // Uppercase query
        List<Product> upperResults = repository.searchByName("KEYBOARD");
        assertEquals(2, upperResults.size());

        // Exact name match
        List<Product> mouseResults = repository.searchByName("Gaming Mouse");
        assertEquals(1, mouseResults.size());
        assertEquals("MS-001", mouseResults.get(0).getSku());
    }

    @Test
    @DisplayName("Should trim leading and trailing spaces from search query")
    void shouldTrimLeadingAndTrailingSpacesInSearchQuery() {
        repository.save(new Product("KB-001", "Mechanical Keyboard", new BigDecimal("79.99"), 10, 2));

        List<Product> results = repository.searchByName("   keyboard   ");
        assertEquals(1, results.size());
        assertEquals("KB-001", results.get(0).getSku());
    }

    @Test
    @DisplayName("Should return empty list when search query is blank or null")
    void shouldReturnEmptyListWhenSearchQueryIsBlankOrNull() {
        repository.save(new Product("KB-001", "Mechanical Keyboard", new BigDecimal("79.99"), 10, 2));

        assertTrue(repository.searchByName(null).isEmpty());
        assertTrue(repository.searchByName("").isEmpty());
        assertTrue(repository.searchByName("   ").isEmpty());
    }

    @Test
    @DisplayName("Should return empty list when no products match the query")
    void shouldReturnEmptyListWhenNoProductsMatch() {
        repository.save(new Product("KB-001", "Mechanical Keyboard", new BigDecimal("79.99"), 10, 2));

        List<Product> results = repository.searchByName("Headphones");
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Should order search results deterministically by SKU ascending")
    void shouldOrderSearchResultsDeterministicallyBySkuAscending() {
        repository.save(new Product("SKU-Z", "Alpha Monitor", new BigDecimal("199.99"), 5, 1));
        repository.save(new Product("SKU-A", "Beta Monitor", new BigDecimal("149.99"), 8, 1));
        repository.save(new Product("SKU-M", "Gamma Monitor", new BigDecimal("179.99"), 3, 1));

        List<Product> results = repository.searchByName("Monitor");
        assertEquals(3, results.size());
        assertEquals("SKU-A", results.get(0).getSku());
        assertEquals("SKU-M", results.get(1).getSku());
        assertEquals("SKU-Z", results.get(2).getSku());
    }

    @Test
    @DisplayName("Should not mutate repository data during search")
    void shouldNotMutateRepositoryDataDuringSearch() {
        Product original = repository.save(new Product("KB-001", "Keyboard", new BigDecimal("25.00"), 10, 2));

        List<Product> results = repository.searchByName("Keyboard");
        assertEquals(1, results.size());

        Product found = repository.findBySku("KB-001").orElseThrow();
        assertEquals(original.getId(), found.getId());
        assertEquals(original.getName(), found.getName());
        assertEquals(original.getPrice(), found.getPrice());
        assertEquals(original.getQuantity(), found.getQuantity());
        assertEquals(original.getMinimumStock(), found.getMinimumStock());
    }

    @Test
    @DisplayName("Should accurately report product count")
    void shouldReportAccurateProductCount() {
        assertEquals(0L, repository.count());

        repository.save(new Product("SKU-1", "Item 1", new BigDecimal("10.00"), 5, 1));
        repository.save(new Product("SKU-2", "Item 2", new BigDecimal("20.00"), 10, 2));

        assertEquals(2L, repository.count());
    }

    @Test
    @DisplayName("Should sort products by SKU ascending and descending")
    void shouldSortProductsBySkuAscAndDesc() {
        repository.save(new Product("SKU-C", "Item C", new BigDecimal("10.00"), 5, 1));
        repository.save(new Product("SKU-A", "Item A", new BigDecimal("10.00"), 5, 1));
        repository.save(new Product("SKU-B", "Item B", new BigDecimal("10.00"), 5, 1));

        PagedResult<Product> asc = repository.findAll(1, 10, SortField.SKU, SortDirection.ASC);
        assertEquals(3, asc.getContent().size());
        assertEquals("SKU-A", asc.getContent().get(0).getSku());
        assertEquals("SKU-B", asc.getContent().get(1).getSku());
        assertEquals("SKU-C", asc.getContent().get(2).getSku());

        PagedResult<Product> desc = repository.findAll(1, 10, SortField.SKU, SortDirection.DESC);
        assertEquals("SKU-C", desc.getContent().get(0).getSku());
        assertEquals("SKU-B", desc.getContent().get(1).getSku());
        assertEquals("SKU-A", desc.getContent().get(2).getSku());
    }

    @Test
    @DisplayName("Should sort products by Name ascending and descending")
    void shouldSortProductsByNameAscAndDesc() {
        repository.save(new Product("SKU-1", "Zebra Gadget", new BigDecimal("10.00"), 5, 1));
        repository.save(new Product("SKU-2", "Apple Fruit", new BigDecimal("10.00"), 5, 1));
        repository.save(new Product("SKU-3", "Mango Juice", new BigDecimal("10.00"), 5, 1));

        PagedResult<Product> asc = repository.findAll(1, 10, SortField.NAME, SortDirection.ASC);
        assertEquals("Apple Fruit", asc.getContent().get(0).getName());
        assertEquals("Mango Juice", asc.getContent().get(1).getName());
        assertEquals("Zebra Gadget", asc.getContent().get(2).getName());

        PagedResult<Product> desc = repository.findAll(1, 10, SortField.NAME, SortDirection.DESC);
        assertEquals("Zebra Gadget", desc.getContent().get(0).getName());
        assertEquals("Mango Juice", desc.getContent().get(1).getName());
        assertEquals("Apple Fruit", desc.getContent().get(2).getName());
    }

    @Test
    @DisplayName("Should sort products by Price ascending and descending")
    void shouldSortProductsByPriceAscAndDesc() {
        repository.save(new Product("SKU-1", "Cheap", new BigDecimal("9.99"), 5, 1));
        repository.save(new Product("SKU-2", "Expensive", new BigDecimal("99.99"), 5, 1));
        repository.save(new Product("SKU-3", "Mid", new BigDecimal("49.99"), 5, 1));

        PagedResult<Product> asc = repository.findAll(1, 10, SortField.PRICE, SortDirection.ASC);
        assertEquals(new BigDecimal("9.99"), asc.getContent().get(0).getPrice());
        assertEquals(new BigDecimal("49.99"), asc.getContent().get(1).getPrice());
        assertEquals(new BigDecimal("99.99"), asc.getContent().get(2).getPrice());

        PagedResult<Product> desc = repository.findAll(1, 10, SortField.PRICE, SortDirection.DESC);
        assertEquals(new BigDecimal("99.99"), desc.getContent().get(0).getPrice());
        assertEquals(new BigDecimal("49.99"), desc.getContent().get(1).getPrice());
        assertEquals(new BigDecimal("9.99"), desc.getContent().get(2).getPrice());
    }

    @Test
    @DisplayName("Should sort products by Quantity ascending and descending")
    void shouldSortProductsByQuantityAscAndDesc() {
        repository.save(new Product("SKU-1", "Item Low", new BigDecimal("10.00"), 3, 1));
        repository.save(new Product("SKU-2", "Item High", new BigDecimal("10.00"), 50, 1));
        repository.save(new Product("SKU-3", "Item Mid", new BigDecimal("10.00"), 20, 1));

        PagedResult<Product> asc = repository.findAll(1, 10, SortField.QUANTITY, SortDirection.ASC);
        assertEquals(3, asc.getContent().get(0).getQuantity());
        assertEquals(20, asc.getContent().get(1).getQuantity());
        assertEquals(50, asc.getContent().get(2).getQuantity());

        PagedResult<Product> desc = repository.findAll(1, 10, SortField.QUANTITY, SortDirection.DESC);
        assertEquals(50, desc.getContent().get(0).getQuantity());
        assertEquals(20, desc.getContent().get(1).getQuantity());
        assertEquals(3, desc.getContent().get(2).getQuantity());
    }

    @Test
    @DisplayName("Should break ties stably using SKU ascending for equal sort values")
    void shouldBreakTiesStablyUsingSkuAscending() {
        // Equal prices, different SKUs
        repository.save(new Product("SKU-Y", "Same Price Item 2", new BigDecimal("25.00"), 10, 1));
        repository.save(new Product("SKU-X", "Same Price Item 1", new BigDecimal("25.00"), 10, 1));

        // When sorting by PRICE ASC: both have 25.00, tie breaker SKU-X < SKU-Y
        PagedResult<Product> asc = repository.findAll(1, 10, SortField.PRICE, SortDirection.ASC);
        assertEquals("SKU-X", asc.getContent().get(0).getSku());
        assertEquals("SKU-Y", asc.getContent().get(1).getSku());

        // When sorting by PRICE DESC: both have 25.00, tie breaker still SKU-X < SKU-Y
        PagedResult<Product> desc = repository.findAll(1, 10, SortField.PRICE, SortDirection.DESC);
        assertEquals("SKU-X", desc.getContent().get(0).getSku());
        assertEquals("SKU-Y", desc.getContent().get(1).getSku());
    }

    @Test
    @DisplayName("Should paginate products across first, middle, and final pages")
    void shouldPaginateProductsAcrossPages() {
        repository.save(new Product("SKU-1", "Product 1", new BigDecimal("10.00"), 10, 1));
        repository.save(new Product("SKU-2", "Product 2", new BigDecimal("10.00"), 10, 1));
        repository.save(new Product("SKU-3", "Product 3", new BigDecimal("10.00"), 10, 1));
        repository.save(new Product("SKU-4", "Product 4", new BigDecimal("10.00"), 10, 1));
        repository.save(new Product("SKU-5", "Product 5", new BigDecimal("10.00"), 10, 1));

        // Page 1 (items 1-2 of 5)
        PagedResult<Product> page1 = repository.findAll(1, 2, SortField.SKU, SortDirection.ASC);
        assertEquals(2, page1.getContent().size());
        assertEquals("SKU-1", page1.getContent().get(0).getSku());
        assertEquals("SKU-2", page1.getContent().get(1).getSku());
        assertEquals(5L, page1.getTotalElements());
        assertEquals(3, page1.getTotalPages());
        assertTrue(page1.hasNext());
        assertFalse(page1.hasPrevious());

        // Page 2 (items 3-4 of 5)
        PagedResult<Product> page2 = repository.findAll(2, 2, SortField.SKU, SortDirection.ASC);
        assertEquals(2, page2.getContent().size());
        assertEquals("SKU-3", page2.getContent().get(0).getSku());
        assertEquals("SKU-4", page2.getContent().get(1).getSku());
        assertTrue(page2.hasNext());
        assertTrue(page2.hasPrevious());

        // Page 3 (final page, item 5 of 5)
        PagedResult<Product> page3 = repository.findAll(3, 2, SortField.SKU, SortDirection.ASC);
        assertEquals(1, page3.getContent().size());
        assertEquals("SKU-5", page3.getContent().get(0).getSku());
        assertFalse(page3.hasNext());
        assertTrue(page3.hasPrevious());
    }

    @Test
    @DisplayName("Should return empty content when requesting beyond final page without error")
    void shouldReturnEmptyContentWhenRequestingBeyondFinalPage() {
        repository.save(new Product("SKU-1", "Product 1", new BigDecimal("10.00"), 10, 1));

        PagedResult<Product> beyond = repository.findAll(2, 5, SortField.SKU, SortDirection.ASC);
        assertNotNull(beyond);
        assertTrue(beyond.getContent().isEmpty());
        assertEquals(1L, beyond.getTotalElements());
        assertEquals(1, beyond.getTotalPages());
        assertFalse(beyond.hasNext());
        assertTrue(beyond.hasPrevious());
    }

    @Test
    @DisplayName("Should return empty paged result when repository has no products")
    void shouldReturnEmptyPagedResultWhenRepositoryIsEmpty() {
        PagedResult<Product> empty = repository.findAll(1, 10, SortField.SKU, SortDirection.ASC);
        assertNotNull(empty);
        assertTrue(empty.getContent().isEmpty());
        assertEquals(0L, empty.getTotalElements());
        assertEquals(0, empty.getTotalPages());
    }

    @Test
    @DisplayName("Should safely handle large offset boundary values without integer overflow")
    void shouldSafelyHandleLargeOffsetBoundaryValues() {
        repository.save(new Product("SKU-1", "Product 1", new BigDecimal("10.00"), 10, 1));

        PagedResult<Product> result = repository.findAll(1_000_000, 100, SortField.SKU, SortDirection.ASC);
        assertNotNull(result);
        assertTrue(result.getContent().isEmpty());
        assertEquals(1L, result.getTotalElements());
    }
}
