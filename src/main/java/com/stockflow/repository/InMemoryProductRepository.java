package com.stockflow.repository;

import com.stockflow.model.InventorySummary;
import com.stockflow.model.Product;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * In-memory implementation of ProductRepository using a Map.
 * Key: Product SKU, Value: Product object.
 */
public class InMemoryProductRepository implements ProductRepository {

    // LinkedHashMap maintains insertion order so products list in the order added
    private final Map<String, Product> productsBySku = new LinkedHashMap<>();
    private long currentId = 1L;

    @Override
    public Product save(Product product) {
        if (product == null || product.getSku() == null || product.getSku().isBlank()) {
            throw new IllegalArgumentException("Product and its SKU cannot be null or blank.");
        }
        if (product.getId() != null) {
            throw new IllegalArgumentException("Cannot save a product that already has an ID: " + product.getId());
        }
        String key = product.getSku().trim();
        if (productsBySku.containsKey(key)) {
            throw new IllegalArgumentException("Product with SKU '" + key + "' already exists.");
        }
        product.setId(currentId++);
        productsBySku.put(key, product);
        return product;
    }

    @Override
    public Optional<Product> findBySku(String sku) {
        if (sku == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(productsBySku.get(sku.trim()));
    }

    @Override
    public boolean existsBySku(String sku) {
        if (sku == null) {
            return false;
        }
        return productsBySku.containsKey(sku.trim());
    }

    @Override
    public List<Product> findAll() {
        return new ArrayList<>(productsBySku.values());
    }

    @Override
    public List<Product> findLowStockProducts() {
        return productsBySku.values().stream()
                .filter(p -> p.getQuantity() <= p.getMinimumStock())
                .sorted(Comparator.comparingInt(Product::getQuantity)
                        .thenComparing(Product::getSku))
                .collect(Collectors.toList());
    }

    @Override
    public InventorySummary getInventorySummary() {
        long totalProducts = productsBySku.size();
        long totalUnits = productsBySku.values().stream()
                .mapToLong(Product::getQuantity)
                .sum();
        long lowStockProducts = productsBySku.values().stream()
                .filter(p -> p.getQuantity() <= p.getMinimumStock())
                .count();
        long outOfStockProducts = productsBySku.values().stream()
                .filter(p -> p.getQuantity() == 0)
                .count();

        return new InventorySummary(totalProducts, totalUnits, lowStockProducts, outOfStockProducts);
    }

    @Override
    public List<Product> searchByName(String nameQuery) {
        if (nameQuery == null || nameQuery.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String lowerQuery = nameQuery.trim().toLowerCase(Locale.ROOT);
        return productsBySku.values().stream()
                .filter(p -> p.getName() != null && p.getName().toLowerCase(Locale.ROOT).contains(lowerQuery))
                .sorted(Comparator.comparing(Product::getSku))
                .collect(Collectors.toList());
    }

    @Override
    public boolean update(Product product) {
        if (product == null || product.getSku() == null) {
            return false;
        }
        String key = product.getSku().trim();
        Product existing = productsBySku.get(key);
        if (existing == null) {
            return false;
        }

        existing.setName(product.getName());
        existing.setPrice(product.getPrice());
        existing.setMinimumStock(product.getMinimumStock());
        return true;
    }

    @Override
    public boolean deleteBySku(String sku) {
        if (sku == null) {
            return false;
        }
        return productsBySku.remove(sku.trim()) != null;
    }

    /**
     * Updates a product's quantity directly in memory.
     * Used exclusively by atomic stock operations, preserving immutability of the Product model.
     */
    public void updateQuantity(Long productId, int newQuantity) {
        if (newQuantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative.");
        }
        Product existing = productsBySku.values().stream()
                .filter(p -> productId.equals(p.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + productId));

        Product updated = new Product(
                existing.getId(),
                existing.getSku(),
                existing.getName(),
                existing.getPrice(),
                newQuantity,
                existing.getMinimumStock()
        );
        productsBySku.put(existing.getSku(), updated);
    }
}
