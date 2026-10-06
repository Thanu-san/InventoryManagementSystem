package com.stockflow.controller;

import com.stockflow.exception.ValidationException;
import com.stockflow.model.Product;
import com.stockflow.service.ProductService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Scanner;

/**
 * Controller responsible for the Product Management console interactions.
 * Receives user input, delegates to ProductService, and prints formatted output.
 */
public class ProductController {

    private final ProductService productService;
    private final Scanner scanner;

    public ProductController(ProductService productService, Scanner scanner) {
        this.productService = productService;
        this.scanner = scanner;
    }

    /**
     * Runs the Product Management menu loop.
     */
    public void runMenu() {
        boolean back = false;
        while (!back) {
            System.out.println("\n--- Product Management ---");
            System.out.println("1. Add Product");
            System.out.println("2. List Products");
            System.out.println("3. Back to Main Menu");
            System.out.print("Select an option: ");

            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1" -> handleAddProduct();
                case "2" -> handleListProducts();
                case "3" -> back = true;
                default -> System.out.println("Invalid option. Please try again.");
            }
        }
    }

    private void handleAddProduct() {
        System.out.println("\n[ Add New Product ]");

        System.out.print("Enter SKU: ");
        String sku = scanner.nextLine();

        System.out.print("Enter Name: ");
        String name = scanner.nextLine();

        BigDecimal price;
        try {
            System.out.print("Enter Price: ");
            String priceInput = scanner.nextLine().trim();
            price = new BigDecimal(priceInput);
        } catch (NumberFormatException e) {
            System.out.println("Error: Price must be a valid number (e.g. 19.99).");
            return;
        }

        int quantity;
        try {
            System.out.print("Enter Quantity: ");
            quantity = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("Error: Quantity must be a valid whole number.");
            return;
        }

        int minimumStock;
        try {
            System.out.print("Enter Minimum Stock: ");
            minimumStock = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("Error: Minimum stock must be a valid whole number.");
            return;
        }

        try {
            Product created = productService.createProduct(sku, name, price, quantity, minimumStock);
            System.out.println("Success: Product created successfully! ID: " + created.getId());
        } catch (ValidationException e) {
            System.out.println("Validation Error: " + e.getMessage());
        }
    }

    private void handleListProducts() {
        System.out.println("\n[ Product List ]");
        List<Product> products = productService.getAllProducts();

        if (products.isEmpty()) {
            System.out.println("No products found in inventory.");
            return;
        }

        // Print header
        System.out.printf("%-6s | %-12s | %-25s | %-10s | %-10s | %-10s%n",
                "ID", "SKU", "NAME", "PRICE", "QUANTITY", "MIN STOCK");
        System.out.println("-".repeat(84));

        for (Product p : products) {
            System.out.printf("%-6d | %-12s | %-25s | $%-9.2f | %-10d | %-10d%n",
                    p.getId(),
                    p.getSku(),
                    p.getName(),
                    p.getPrice(),
                    p.getQuantity(),
                    p.getMinimumStock());
        }
    }
}
