package com.stockflow.repository;

import com.stockflow.model.InventorySummary;
import com.stockflow.model.PagedResult;
import com.stockflow.model.Product;
import com.stockflow.model.SortDirection;
import com.stockflow.model.SortField;

import java.util.List;
import java.util.Optional;

/**
 * Defines data access operations for Product.
 * Decouples the storage mechanism (In-Memory, JDBC, etc.) from business logic.
 */
public interface ProductRepository {

    /**
     * Saves a new product. Assigns a generated ID and stores it.
     * Rejects products that already have an assigned ID.
     */
    Product save(Product product);

    /**
     * Finds a product by its SKU.
     */
    Optional<Product> findBySku(String sku);

    /**
     * Checks if a product exists with the given SKU.
     */
    boolean existsBySku(String sku);

    /**
     * Returns all stored products.
     */
    List<Product> findAll();

    /**
     * Updates editable fields of an existing product.
     * Returns true if the product was found and updated, false otherwise.
     */
    boolean update(Product product);

    /**
     * Deletes a product by its SKU.
     * Returns true if the product was found and removed, false otherwise.
     */
    boolean deleteBySku(String sku);

    /**
     * Finds all products whose current quantity is less than or equal to their minimum stock.
     * Results are ordered by quantity ascending, then SKU ascending.
     */
    List<Product> findLowStockProducts();

    /**
     * Calculates and returns aggregate inventory metrics:
     * total products, total units, low-stock count, and out-of-stock count.
     */
    InventorySummary getInventorySummary();

    /**
     * Searches for products whose name contains the specified search term (case-insensitive).
     * Returns matching products ordered by SKU ascending.
     *
     * @param nameQuery the partial or full product name to search for
     * @return list of matching products ordered by SKU ascending
     */
    List<Product> searchByName(String nameQuery);

    /**
     * Returns a paginated and sorted slice of products.
     *
     * @param page 1-based page number
     * @param size number of items per page
     * @param sortField product field to sort by
     * @param sortDirection direction of sort (ASC or DESC)
     * @return paged result containing matching products and pagination metadata
     */
    PagedResult<Product> findAll(int page, int size, SortField sortField, SortDirection sortDirection);

    /**
     * Returns the total count of products in the repository.
     *
     * @return total product count
     */
    long count();

    /**
     * Updates the assigned supplier for a product identified by SKU.
     * Pass null to remove/clear the supplier assignment.
     *
     * @param sku product SKU
     * @param supplierId supplier ID to assign, or null to clear
     * @return true if product was found and updated, false otherwise
     */
    boolean updateSupplier(String sku, Long supplierId);

    /**
     * Checks if any product is currently assigned to the given supplier ID.
     *
     * @param supplierId supplier ID to check
     * @return true if at least one product references this supplier, false otherwise
     */
    boolean existsBySupplierId(Long supplierId);
}
