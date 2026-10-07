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
     * Saves a product. If it has no ID, assigns an ID and stores it.
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
     * Deletes a product by its SKU.
     * Returns true if the product was found and removed, false otherwise.
     */
    boolean deleteBySku(String sku);
}
