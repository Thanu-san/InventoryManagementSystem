package com.stockflow.model;

import java.math.BigDecimal;

/**
 * Represents a product in the inventory.
 * Encapsulates core product attributes with private fields and public getters/setters.
 */
public class Product {
    private Long id;
    private String sku;
    private String name;
    private BigDecimal price;
    private int quantity;
    private int minimumStock;

    // Default constructor
    public Product() {
    }

    // Constructor without ID (useful before the product is stored/assigned an ID)
    public Product(String sku, String name, BigDecimal price, int quantity, int minimumStock) {
        this.sku = sku;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
        this.minimumStock = minimumStock;
    }

    // Full constructor
    public Product(Long id, String sku, String name, BigDecimal price, int quantity, int minimumStock) {
        this.id = id;
        this.sku = sku;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
        this.minimumStock = minimumStock;
    }

    // --- Getters and Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getMinimumStock() {
        return minimumStock;
    }

    public void setMinimumStock(int minimumStock) {
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
