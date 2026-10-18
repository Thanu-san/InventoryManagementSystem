package com.stockflow.repository;

import com.stockflow.model.Product;

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
}
