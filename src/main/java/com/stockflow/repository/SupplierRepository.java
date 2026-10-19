package com.stockflow.repository;

import com.stockflow.model.Supplier;

import java.util.List;
import java.util.Optional;

/**
 * Defines data access operations for Supplier.
 * Decouples the storage mechanism (In-Memory, JDBC, etc.) from business logic.
 */
public interface SupplierRepository {

    /**
     * Saves a new supplier. Assigns a generated ID and stores it.
     * Rejects suppliers that already have an assigned ID.
     */
    Supplier save(Supplier supplier);

    /**
     * Finds a supplier by its supplier code.
     */
    Optional<Supplier> findBySupplierCode(String supplierCode);

    /**
     * Checks if a supplier exists with the given supplier code.
     */
    boolean existsBySupplierCode(String supplierCode);

    /**
     * Returns all stored suppliers.
     */
    List<Supplier> findAll();

    /**
     * Updates editable fields of an existing supplier.
     * Returns true if the supplier was found and updated, false otherwise.
     */
    boolean update(Supplier supplier);

    /**
     * Deletes a supplier by its supplier code.
     * Returns true if the supplier was found and removed, false otherwise.
     */
    boolean deleteBySupplierCode(String supplierCode);
}
