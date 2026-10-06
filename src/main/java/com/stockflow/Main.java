package com.stockflow;

import com.stockflow.controller.ProductController;
import com.stockflow.repository.InMemoryProductRepository;
import com.stockflow.repository.ProductRepository;
import com.stockflow.service.ProductService;

import java.util.Scanner;

/**
 * Entry point for StockFlow console application.
 * Assembles layers and manages the main menu.
 */
public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        // 1. Dependency wiring: Repository -> Service -> Controller
        ProductRepository productRepository = new InMemoryProductRepository();
        ProductService productService = new ProductService(productRepository);
        ProductController productController = new ProductController(productService, scanner);

        System.out.println("============================================");
        System.out.println("   Welcome to StockFlow Management System   ");
        System.out.println("============================================");

        boolean running = true;
        while (running) {
            System.out.println("\n=== Main Menu ===");
            System.out.println("1. Product Management");
            System.out.println("2. Exit");
            System.out.print("Enter your choice: ");

            if (!scanner.hasNextLine()) {
                break;
            }
            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1" -> productController.runMenu();
                case "2" -> {
                    System.out.println("Thank you for using StockFlow. Goodbye!");
                    running = false;
                }
                default -> System.out.println("Invalid choice. Please enter 1 or 2.");
            }
        }

        scanner.close();
    }
}
