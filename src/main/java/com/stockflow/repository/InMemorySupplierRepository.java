package com.stockflow.repository;

import com.stockflow.model.Supplier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory implementation of SupplierRepository using a Map.
 * Key: Supplier Code, Value: Supplier object.
 */
public class InMemorySupplierRepository implements SupplierRepository {

    // LinkedHashMap maintains insertion order so suppliers list in the order added
    private final Map<String, Supplier> suppliersByCode = new LinkedHashMap<>();
    private long currentId = 1L;

    @Override
    public Supplier save(Supplier supplier) {
        if (supplier == null || supplier.getSupplierCode() == null || supplier.getSupplierCode().isBlank()) {
            throw new IllegalArgumentException("Supplier and its code cannot be null or blank.");
        }
        if (supplier.getId() != null) {
            throw new IllegalArgumentException("Cannot save a supplier that already has an ID: " + supplier.getId());
        }
        String key = supplier.getSupplierCode().trim();
        if (suppliersByCode.containsKey(key)) {
            throw new IllegalArgumentException("Supplier with code '" + key + "' already exists.");
        }
        supplier.setId(currentId++);
        suppliersByCode.put(key, supplier);
        return supplier;
    }

    @Override
    public Optional<Supplier> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return suppliersByCode.values().stream()
                .filter(s -> id.equals(s.getId()))
                .findFirst();
    }

    @Override
    public Optional<Supplier> findBySupplierCode(String supplierCode) {
        if (supplierCode == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(suppliersByCode.get(supplierCode.trim()));
    }

    @Override
    public boolean existsBySupplierCode(String supplierCode) {
        if (supplierCode == null) {
            return false;
        }
        return suppliersByCode.containsKey(supplierCode.trim());
    }

    @Override
    public List<Supplier> findAll() {
        return new ArrayList<>(suppliersByCode.values());
    }

    @Override
    public boolean update(Supplier supplier) {
        if (supplier == null || supplier.getSupplierCode() == null) {
            return false;
        }
        String key = supplier.getSupplierCode().trim();
        Supplier existing = suppliersByCode.get(key);
        if (existing == null) {
            return false;
        }

        existing.setName(supplier.getName());
        existing.setContactName(supplier.getContactName());
        existing.setPhone(supplier.getPhone());
        existing.setEmail(supplier.getEmail());
        existing.setAddress(supplier.getAddress());
        return true;
    }

    @Override
    public boolean deleteBySupplierCode(String supplierCode) {
        if (supplierCode == null) {
            return false;
        }
        return suppliersByCode.remove(supplierCode.trim()) != null;
    }
}
