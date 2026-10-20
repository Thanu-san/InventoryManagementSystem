package com.stockflow;

import com.stockflow.controller.ProductController;
import com.stockflow.controller.PurchaseOrderController;
import com.stockflow.controller.SupplierController;
import com.stockflow.repository.InventoryTransactionRepository;
import com.stockflow.repository.JdbcInventoryTransactionRepository;
import com.stockflow.repository.JdbcProductRepository;
import com.stockflow.repository.JdbcPurchaseOrderRepository;
import com.stockflow.repository.JdbcSupplierRepository;
import com.stockflow.repository.ProductRepository;
import com.stockflow.repository.PurchaseOrderRepository;
import com.stockflow.repository.SupplierRepository;
import com.stockflow.service.ProductService;
import com.stockflow.service.PurchaseOrderService;
import com.stockflow.service.SupplierService;

import java.util.Scanner;

/**
 * Entry point for StockFlow console application.
 * Assembles layers and manages the main menu.
 */
public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // 1. Dependency wiring: Repository -> Service -> Controller
        ProductRepository productRepository = new JdbcProductRepository();
        SupplierRepository supplierRepository = new JdbcSupplierRepository();
        InventoryTransactionRepository transactionRepository = new JdbcInventoryTransactionRepository();
        PurchaseOrderRepository purchaseOrderRepository = new JdbcPurchaseOrderRepository();

        ProductService productService = new ProductService(productRepository, transactionRepository, supplierRepository, purchaseOrderRepository);
        ProductController productController = new ProductController(productService, scanner);

        SupplierService supplierService = new SupplierService(supplierRepository, productRepository, purchaseOrderRepository);
        SupplierController supplierController = new SupplierController(supplierService, scanner);

        PurchaseOrderService purchaseOrderService = new PurchaseOrderService(purchaseOrderRepository, supplierRepository, productRepository);
        PurchaseOrderController purchaseOrderController = new PurchaseOrderController(purchaseOrderService, scanner);

        System.out.println("============================================");
        System.out.println("   Welcome to StockFlow Management System   ");
        System.out.println("============================================");

        boolean running = true;
        while (running) {
            System.out.println("\n=== Main Menu ===");
            System.out.println("1. Product Management");
            System.out.println("2. Supplier Management");
            System.out.println("3. Purchase Order Management");
            System.out.println("4. Exit");
            System.out.print("Enter your choice: ");

            if (!scanner.hasNextLine()) {
                break;
            }
            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1" -> {
                    try {
                        productController.runMenu();
                    } catch (Exception e) {
                        System.out.println("Unexpected error: " + e.getMessage());
                    }
                }
                case "2" -> {
                    try {
                        supplierController.runMenu();
                    } catch (Exception e) {
                        System.out.println("Unexpected error: " + e.getMessage());
                    }
                }
                case "3" -> {
                    try {
                        purchaseOrderController.runMenu();
                    } catch (Exception e) {
                        System.out.println("Unexpected error: " + e.getMessage());
                    }
                }
                case "4" -> {
                    System.out.println("Thank you for using StockFlow. Goodbye!");
                    running = false;
                }
                default -> System.out.println("Invalid choice. Please enter 1, 2, 3, or 4.");
            }
        }

        scanner.close();
    }
}
