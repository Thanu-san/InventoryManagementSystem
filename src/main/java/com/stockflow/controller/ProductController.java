package com.stockflow.controller;

import com.stockflow.exception.ValidationException;
import com.stockflow.model.InventorySummary;
import com.stockflow.model.InventoryTransaction;
import com.stockflow.model.InventoryValuationItem;
import com.stockflow.model.InventoryValuationReport;
import com.stockflow.model.PagedResult;
import com.stockflow.model.Product;
import com.stockflow.model.SortDirection;
import com.stockflow.model.SortField;
import com.stockflow.service.ProductService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
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
            System.out.println("\n--- Product & Inventory Management ---");
            System.out.println("1. Add Product");
            System.out.println("2. List Products");
            System.out.println("3. Find Product by SKU");
            System.out.println("4. Update Product");
            System.out.println("5. Delete Product");
            System.out.println("6. Stock In");
            System.out.println("7. Stock Out");
            System.out.println("8. View Transaction History");
            System.out.println("9. Low-Stock Report");
            System.out.println("10. Inventory Summary Report");
            System.out.println("11. Inventory Valuation Report");
            System.out.println("12. Search Products by Name");
            System.out.println("13. Browse Products (Paged & Sorted)");
            System.out.println("14. Back to Main Menu");
            System.out.print("Select an option: ");

            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1" -> handleAddProduct();
                case "2" -> handleListProducts();
                case "3" -> handleFindProductBySku();
                case "4" -> handleUpdateProduct();
                case "5" -> handleDeleteProduct();
                case "6" -> handleStockIn();
                case "7" -> handleStockOut();
                case "8" -> handleViewHistory();
                case "9" -> handleLowStockReport();
                case "10" -> handleInventorySummaryReport();
                case "11" -> handleInventoryValuationReport();
                case "12" -> handleSearchProductsByName();
                case "13" -> handleBrowseProducts();
                case "14" -> back = true;
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

    private void handleFindProductBySku() {
        System.out.println("\n[ Find Product by SKU ]");
        System.out.print("Enter SKU: ");
        String sku = scanner.nextLine();

        try {
            Optional<Product> productOpt = productService.findProductBySku(sku);
            if (productOpt.isPresent()) {
                Product p = productOpt.get();
                System.out.println("\nProduct Found:");
                System.out.println("-------------------------");
                System.out.println("ID: " + p.getId());
                System.out.println("SKU: " + p.getSku());
                System.out.println("Name: " + p.getName());
                System.out.printf("Price: $%.2f%n", p.getPrice());
                System.out.println("Quantity: " + p.getQuantity());
                System.out.println("Minimum Stock: " + p.getMinimumStock());
                System.out.println("-------------------------");
            } else {
                System.out.println("Product not found with SKU: " + sku.trim());
            }
        } catch (ValidationException e) {
            System.out.println("Validation Error: " + e.getMessage());
        }
    }

    private void handleSearchProductsByName() {
        System.out.println("\n[ Search Products by Name ]");
        System.out.print("Enter product name to search: ");
        String nameQuery = scanner.nextLine();

        try {
            List<Product> products = productService.searchProductsByName(nameQuery);
            if (products.isEmpty()) {
                System.out.println("No products found matching: " + nameQuery.trim());
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
        } catch (ValidationException e) {
            System.out.println("Validation Error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handleUpdateProduct() {
        System.out.println("\n[ Update Product ]");
        System.out.print("Enter SKU of product to update: ");
        String sku = scanner.nextLine();

        Optional<Product> productOpt;
        try {
            productOpt = productService.findProductBySku(sku);
        } catch (ValidationException e) {
            System.out.println("Validation Error: " + e.getMessage());
            return;
        }

        if (productOpt.isEmpty()) {
            System.out.println("Product not found with SKU: " + (sku != null ? sku.trim() : ""));
            return;
        }

        Product current = productOpt.get();
        System.out.println("\nCurrent Product:");
        System.out.println("-------------------------");
        System.out.println("ID: " + current.getId());
        System.out.println("SKU: " + current.getSku());
        System.out.println("Name: " + current.getName());
        System.out.printf("Price: $%.2f%n", current.getPrice());
        System.out.println("Quantity: " + current.getQuantity());
        System.out.println("Minimum Stock: " + current.getMinimumStock());
        System.out.println("-------------------------");

        System.out.print("Enter new name: ");
        String newName = scanner.nextLine();

        BigDecimal newPrice;
        try {
            System.out.print("Enter new price: ");
            String priceInput = scanner.nextLine().trim();
            newPrice = new BigDecimal(priceInput);
        } catch (NumberFormatException e) {
            System.out.println("Error: Price must be a valid number (e.g. 19.99).");
            return;
        }

        int newMinimumStock;
        try {
            System.out.print("Enter new minimum stock: ");
            newMinimumStock = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("Error: Minimum stock must be a valid whole number.");
            return;
        }

        try {
            productService.updateProduct(sku, newName, newPrice, newMinimumStock);
            System.out.println("Product updated successfully.");
        } catch (ValidationException e) {
            System.out.println("Validation Error: " + e.getMessage());
        }
    }

    private void handleDeleteProduct() {
        System.out.println("\n[ Delete Product ]");
        System.out.print("Enter SKU of product to delete: ");
        String sku = scanner.nextLine();

        Optional<Product> productOpt;
        try {
            productOpt = productService.findProductBySku(sku);
        } catch (ValidationException e) {
            System.out.println("Validation Error: " + e.getMessage());
            return;
        }

        if (productOpt.isEmpty()) {
            System.out.println("Product not found with SKU: " + (sku != null ? sku.trim() : ""));
            return;
        }

        Product p = productOpt.get();
        System.out.println("\nProduct to Delete:");
        System.out.println("-------------------------");
        System.out.println("ID: " + p.getId());
        System.out.println("SKU: " + p.getSku());
        System.out.println("Name: " + p.getName());
        System.out.printf("Price: $%.2f%n", p.getPrice());
        System.out.println("Quantity: " + p.getQuantity());
        System.out.println("Minimum Stock: " + p.getMinimumStock());
        System.out.println("-------------------------");

        while (true) {
            System.out.print("Are you sure you want to delete this product? (y/n): ");
            if (!scanner.hasNextLine()) {
                break;
            }
            String confirm = scanner.nextLine().trim();
            if (confirm.equalsIgnoreCase("y")) {
                try {
                    productService.deleteProductBySku(sku);
                    System.out.println("Product deleted successfully.");
                } catch (ValidationException e) {
                    System.out.println("Validation Error: " + e.getMessage());
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
                break;
            } else if (confirm.equalsIgnoreCase("n")) {
                System.out.println("Delete cancelled.");
                break;
            } else {
                System.out.println("Please enter Y or N.");
            }
        }
    }

    private void handleStockIn() {
        System.out.println("\n[ Stock In ]");
        System.out.print("Enter Product SKU: ");
        String sku = scanner.nextLine();

        int quantity;
        try {
            System.out.print("Enter Quantity to Add: ");
            quantity = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("Error: Quantity must be a valid whole number.");
            return;
        }

        try {
            InventoryTransaction tx = productService.stockIn(sku, quantity);
            System.out.println("Success: Stock In recorded successfully! Transaction ID: " + tx.getId());
        } catch (ValidationException e) {
            System.out.println("Validation Error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handleStockOut() {
        System.out.println("\n[ Stock Out ]");
        System.out.print("Enter Product SKU: ");
        String sku = scanner.nextLine();

        int quantity;
        try {
            System.out.print("Enter Quantity to Remove: ");
            quantity = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("Error: Quantity must be a valid whole number.");
            return;
        }

        try {
            InventoryTransaction tx = productService.stockOut(sku, quantity);
            System.out.println("Success: Stock Out recorded successfully! Transaction ID: " + tx.getId());
        } catch (ValidationException e) {
            System.out.println("Validation Error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handleViewHistory() {
        System.out.println("\n[ Transaction History ]");
        System.out.print("Enter Product SKU: ");
        String sku = scanner.nextLine();

        try {
            List<InventoryTransaction> transactions = productService.getTransactionHistory(sku);
            if (transactions.isEmpty()) {
                System.out.println("No transactions found for product SKU: " + sku.trim());
                return;
            }

            System.out.printf("%-6s | %-10s | %-12s | %-10s | %-25s%n",
                    "ID", "PROD ID", "TYPE", "QUANTITY", "CREATED AT");
            System.out.println("-".repeat(72));

            for (InventoryTransaction tx : transactions) {
                System.out.printf("%-6d | %-10d | %-12s | %-10d | %-25s%n",
                        tx.getId(),
                        tx.getProductId(),
                        tx.getType(),
                        tx.getQuantity(),
                        tx.getCreatedAt().toString());
            }
        } catch (ValidationException e) {
            System.out.println("Validation Error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handleLowStockReport() {
        System.out.println("\n[ Low-Stock Report ]");
        List<Product> lowStockProducts = productService.getLowStockProducts();

        if (lowStockProducts.isEmpty()) {
            System.out.println("No low-stock products found.");
            return;
        }

        // Print header
        System.out.printf("%-6s | %-12s | %-25s | %-10s | %-10s | %-10s%n",
                "ID", "SKU", "NAME", "PRICE", "QUANTITY", "MIN STOCK");
        System.out.println("-".repeat(84));

        for (Product p : lowStockProducts) {
            System.out.printf("%-6d | %-12s | %-25s | $%-9.2f | %-10d | %-10d%n",
                    p.getId(),
                    p.getSku(),
                    p.getName(),
                    p.getPrice(),
                    p.getQuantity(),
                    p.getMinimumStock());
        }
    }

    private void handleInventorySummaryReport() {
        System.out.println("\n[ Inventory Summary Report ]");
        try {
            InventorySummary summary = productService.getInventorySummary();
            System.out.println("Total Products:        " + summary.getTotalProducts());
            System.out.println("Total Units in Stock:  " + summary.getTotalUnits());
            System.out.println("Low-Stock Products:    " + summary.getLowStockProducts());
            System.out.println("Out-of-Stock Products: " + summary.getOutOfStockProducts());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handleInventoryValuationReport() {
        System.out.println("\n[ Inventory Valuation Report ]");
        try {
            InventoryValuationReport report = productService.getInventoryValuationReport();
            if (report.getItems().isEmpty()) {
                System.out.println("No products found in inventory.");
                return;
            }

            System.out.printf("%-12s | %-25s | %-12s | %-10s | %-15s%n",
                    "SKU", "PRODUCT", "UNIT PRICE", "QUANTITY", "INVENTORY VALUE");
            System.out.println("-".repeat(86));

            for (InventoryValuationItem item : report.getItems()) {
                System.out.printf("%-12s | %-25s | $%-11.2f | %-10d | $%-14.2f%n",
                        item.getSku(),
                        item.getName(),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        item.getInventoryValue());
            }

            System.out.println("-".repeat(86));
            System.out.printf("%-12s | %-25s | %-12s | %-10d | $%-14.2f%n",
                    "", "Total", "", report.getTotalQuantity(), report.getTotalValue());
            System.out.println("-".repeat(86));
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handleBrowseProducts() {
        System.out.println("\n[ Browse Products (Paged & Sorted) ]");

        System.out.println("Select sort field:");
        System.out.println("1. SKU (default)");
        System.out.println("2. Name");
        System.out.println("3. Price");
        System.out.println("4. Quantity");
        System.out.print("Enter choice [1-4, default 1]: ");
        String fieldInput = scanner.nextLine().trim();
        SortField sortField = switch (fieldInput) {
            case "2" -> SortField.NAME;
            case "3" -> SortField.PRICE;
            case "4" -> SortField.QUANTITY;
            default -> SortField.SKU;
        };

        System.out.println("Select sort direction:");
        System.out.println("1. Ascending (default)");
        System.out.println("2. Descending");
        System.out.print("Enter choice [1-2, default 1]: ");
        String dirInput = scanner.nextLine().trim();
        SortDirection sortDirection = "2".equals(dirInput) ? SortDirection.DESC : SortDirection.ASC;

        System.out.print("Enter page size [default 5]: ");
        String sizeInput = scanner.nextLine().trim();
        int pageSize = 5;
        if (!sizeInput.isEmpty()) {
            try {
                int parsed = Integer.parseInt(sizeInput);
                if (parsed > 0) {
                    pageSize = parsed;
                } else {
                    System.out.println("Invalid page size. Using default: 5.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid number. Using default page size: 5.");
            }
        }

        int currentPage = 1;
        boolean browsing = true;
        while (browsing) {
            try {
                PagedResult<Product> result = productService.getProductsPaged(currentPage, pageSize, sortField, sortDirection);

                if (result.getTotalElements() == 0) {
                    System.out.println("No products found in inventory.");
                    return;
                }

                System.out.printf("\n--- Products (Sorted by %s %s) ---%n", sortField, sortDirection);
                System.out.printf("%-6s | %-12s | %-25s | %-10s | %-10s | %-10s%n",
                        "ID", "SKU", "NAME", "PRICE", "QUANTITY", "MIN STOCK");
                System.out.println("-".repeat(84));

                for (Product p : result.getContent()) {
                    System.out.printf("%-6d | %-12s | %-25s | $%-9.2f | %-10d | %-10d%n",
                            p.getId(),
                            p.getSku(),
                            p.getName(),
                            p.getPrice(),
                            p.getQuantity(),
                            p.getMinimumStock());
                }

                System.out.println("-".repeat(84));
                System.out.printf("Page %d of %d (Total Products: %d)%n",
                        result.getPageNumber(), result.getTotalPages(), result.getTotalElements());

                System.out.print("Navigation: [N]ext | [P]revious | [J]ump to page | [B]ack to Menu: ");
                String action = scanner.nextLine().trim().toUpperCase();

                switch (action) {
                    case "N" -> {
                        if (result.hasNext()) {
                            currentPage++;
                        } else {
                            System.out.println("You are already on the last page.");
                        }
                    }
                    case "P" -> {
                        if (result.hasPrevious()) {
                            currentPage--;
                        } else {
                            System.out.println("You are already on the first page.");
                        }
                    }
                    case "J" -> {
                        System.out.printf("Enter page number (1-%d): ", result.getTotalPages());
                        String pageInput = scanner.nextLine().trim();
                        try {
                            int targetPage = Integer.parseInt(pageInput);
                            if (targetPage >= 1 && targetPage <= result.getTotalPages()) {
                                currentPage = targetPage;
                            } else {
                                System.out.println("Invalid page number. Must be between 1 and " + result.getTotalPages());
                            }
                        } catch (NumberFormatException e) {
                            System.out.println("Invalid number format.");
                        }
                    }
                    case "B" -> browsing = false;
                    default -> System.out.println("Invalid option. Please enter N, P, J, or B.");
                }
            } catch (ValidationException e) {
                System.out.println("Validation Error: " + e.getMessage());
                browsing = false;
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
                browsing = false;
            }
        }
    }
}
