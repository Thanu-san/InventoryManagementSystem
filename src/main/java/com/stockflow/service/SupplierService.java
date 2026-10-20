package com.stockflow.service;

import com.stockflow.exception.ValidationException;
import com.stockflow.model.Supplier;
import com.stockflow.repository.ProductRepository;
import com.stockflow.repository.SupplierRepository;

import java.util.List;
import java.util.Optional;

/**
 * Service layer responsible for supplier business logic, validation, and coordinating data storage.
 */
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final com.stockflow.repository.PurchaseOrderRepository purchaseOrderRepository;

    public SupplierService(SupplierRepository supplierRepository) {
        this(supplierRepository, null, null);
    }

    public SupplierService(SupplierRepository supplierRepository, ProductRepository productRepository) {
        this(supplierRepository, productRepository, null);
    }

    public SupplierService(SupplierRepository supplierRepository,
                           ProductRepository productRepository,
                           com.stockflow.repository.PurchaseOrderRepository purchaseOrderRepository) {
        if (supplierRepository == null) {
            throw new IllegalArgumentException("Supplier repository cannot be null.");
        }
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
    }

    /**
     * Creates and saves a new supplier after validating all business rules.
     */
    public Supplier createSupplier(String supplierCode, String name, String contactName,
                                   String phone, String email, String address) {
        // Rule 1: Supplier code must not be blank
        if (supplierCode == null || supplierCode.trim().isEmpty()) {
            throw new ValidationException("Supplier code cannot be blank.");
        }

        // Rule 2: Supplier code must be unique
        if (supplierRepository.existsBySupplierCode(supplierCode.trim())) {
            throw new ValidationException("Supplier with code '" + supplierCode.trim() + "' already exists.");
        }

        // Rule 3: Name must not be blank
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("Supplier name cannot be blank.");
        }

        Supplier supplier = new Supplier(
                supplierCode.trim(), name.trim(),
                normalizeOptional(contactName),
                normalizeOptional(phone),
                normalizeOptional(email),
                normalizeOptional(address)
        );
        return supplierRepository.save(supplier);
    }

    /**
     * Returns all suppliers currently in the system.
     */
    public List<Supplier> getAllSuppliers() {
        return supplierRepository.findAll();
    }

    /**
     * Finds a supplier by its supplier code.
     * Validates that the input code is not blank and normalizes surrounding spaces.
     */
    public Optional<Supplier> findSupplierByCode(String supplierCode) {
        if (supplierCode == null || supplierCode.trim().isEmpty()) {
            throw new ValidationException("Supplier code cannot be blank.");
        }
        return supplierRepository.findBySupplierCode(supplierCode.trim());
    }

    /**
     * Updates editable fields of an existing supplier.
     * Preserves ID and supplier code invariants.
     */
    public Supplier updateSupplier(String supplierCode, String name, String contactName,
                                   String phone, String email, String address) {
        if (supplierCode == null || supplierCode.trim().isEmpty()) {
            throw new ValidationException("Supplier code cannot be blank.");
        }

        Supplier supplier = supplierRepository.findBySupplierCode(supplierCode.trim())
                .orElseThrow(() -> new ValidationException("Supplier not found with code: " + supplierCode.trim()));

        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("Supplier name cannot be blank.");
        }

        supplier.setName(name.trim());
        supplier.setContactName(normalizeOptional(contactName));
        supplier.setPhone(normalizeOptional(phone));
        supplier.setEmail(normalizeOptional(email));
        supplier.setAddress(normalizeOptional(address));

        boolean updated = supplierRepository.update(supplier);
        if (!updated) {
            throw new ValidationException("Supplier not found with code: " + supplierCode.trim());
        }

        return supplier;
    }

    /**
     * Deletes a supplier by its supplier code.
     * Validates that the code is not blank and verifies the supplier exists.
     */
    public void deleteSupplierByCode(String supplierCode) {
        if (supplierCode == null || supplierCode.trim().isEmpty()) {
            throw new ValidationException("Supplier code cannot be blank.");
        }

        Supplier supplier = supplierRepository.findBySupplierCode(supplierCode.trim())
                .orElseThrow(() -> new ValidationException("Supplier not found with code: " + supplierCode.trim()));

        if (productRepository != null && productRepository.existsBySupplierId(supplier.getId())) {
            throw new ValidationException(
                    "Cannot delete supplier '" + supplierCode.trim() + "' because it is currently assigned to one or more products.");
        }

        if (purchaseOrderRepository != null && purchaseOrderRepository.existsBySupplierId(supplier.getId())) {
            throw new ValidationException(
                    "Cannot delete supplier '" + supplierCode.trim() + "' because it is referenced by one or more purchase orders.");
        }

        boolean deleted = supplierRepository.deleteBySupplierCode(supplierCode.trim());
        if (!deleted) {
            throw new ValidationException("Supplier not found with code: " + supplierCode.trim());
        }
    }

    /**
     * Normalizes optional string fields: blank or null becomes null, otherwise trimmed.
     */
    private static String normalizeOptional(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
