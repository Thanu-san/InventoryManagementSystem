package com.stockflow.repository;

import com.stockflow.model.Product;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        if (product.getId() == null) {
            product.setId(currentId++);
        }
        productsBySku.put(product.getSku(), product);
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
}
