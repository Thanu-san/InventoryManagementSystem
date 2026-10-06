package com.stockflow.model;

import java.math.BigDecimal;

/**
 * Represents a product in the inventory.
 * Encapsulates domain state and protects its own internal integrity:
 * - SKU is immutable once created (serves as domain identity).
 * - ID can only be assigned once upon persistence.
 * - Quantity cannot be mutated arbitrarily; stock changes occur through operations.
 * - Name, price, and minimum stock have guarded setters preventing invalid values.
 */
public class Product {
    private Long id;
    private final String sku;
    private String name;
    private BigDecimal price;
    private final int quantity;
    private int minimumStock;

    // Constructor without ID (used when creating a new product before persistence)
    public Product(String sku, String name, BigDecimal price, int quantity, int minimumStock) {
        this(null, sku, name, price, quantity, minimumStock);
    }

    // Full constructor (used when reconstituting an existing product, e.g. from storage)
    public Product(Long id, String sku, String name, BigDecimal price, int quantity, int minimumStock) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("Product SKU cannot be blank.");
        }
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity must be greater than or equal to 0.");
        }

        if (id != null) {
            setId(id);
        }
        this.sku = sku.trim();
        this.quantity = quantity;
        setName(name);
        setPrice(price);
        setMinimumStock(minimumStock);
    }

    // --- ID: Can only be assigned once ---
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        if (this.id != null) {
            throw new IllegalStateException("Product ID is already assigned and cannot be changed.");
        }
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Product ID must be greater than zero.");
        }
        this.id = id;
    }

    // --- SKU: Immutable identity ---
    public String getSku() {
        return sku;
    }

    // --- Name: Guarded setter ---
    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Product name cannot be blank.");
        }
        this.name = name.trim();
    }

    // --- Price: Guarded setter ---
    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Product price must be greater than or equal to 0.");
        }
        this.price = price;
    }

    // --- Quantity: No public setter; quantity changes only via operations ---
    public int getQuantity() {
        return quantity;
    }

    // --- Minimum Stock: Guarded setter ---
    public int getMinimumStock() {
        return minimumStock;
    }

    public void setMinimumStock(int minimumStock) {
        if (minimumStock < 0) {
            throw new IllegalArgumentException("Minimum stock must be greater than or equal to 0.");
        }
        this.minimumStock = minimumStock;
    }

    @Override
    public String toString() {
        return "Product{" +
                "id=" + id +
                ", sku='" + sku + '\'' +
                ", name='" + name + '\'' +
                ", price=" + price +
                ", quantity=" + quantity +
                ", minimumStock=" + minimumStock +
                '}';
    }
}
