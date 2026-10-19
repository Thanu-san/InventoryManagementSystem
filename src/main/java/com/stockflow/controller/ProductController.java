package com.stockflow.controller;

import com.stockflow.exception.DatabaseException;
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
 * Provides robust validation, failure recovery, and friendly user error messages.
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

            if (!scanner.hasNextLine()) {
                break;
            }
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

        Optional<String> skuOpt = promptNonBlankString("Enter SKU: ", "SKU");
        if (skuOpt.isEmpty()) {
            return;
        }

        Optional<String> nameOpt = promptNonBlankString("Enter Name: ", "Product name");
        if (nameOpt.isEmpty()) {
            return;
        }

        Optional<BigDecimal> priceOpt = promptPrice("Enter Price: ");
        if (priceOpt.isEmpty()) {
            return;
        }

        Optional<Integer> qtyOpt = promptInteger("Enter Quantity: ", "Quantity", 0);
        if (qtyOpt.isEmpty()) {
            return;
        }

        Optional<Integer> minStockOpt = promptInteger("Enter Minimum Stock: ", "Minimum stock", 0);
        if (minStockOpt.isEmpty()) {
            return;
        }

        try {
            Product created = productService.createProduct(
                    skuOpt.get(), nameOpt.get(), priceOpt.get(), qtyOpt.get(), minStockOpt.get());
            System.out.println("Success: Product created successfully! ID: " + created.getId());
        } catch (ValidationException e) {
            System.out.println("Validation Error: " + e.getMessage());
        } catch (DatabaseException e) {
            System.out.println("Database Error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handleListProducts() {
        System.out.println("\n[ Product List ]");
        try {
            List<Product> products = productService.getAllProducts();

            if (products.isEmpty()) {
                System.out.println("No products found in inventory.");
                return;
            }

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
        } catch (DatabaseException e) {
            System.out.println("Database Error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handleFindProductBySku() {
        System.out.println("\n[ Find Product by SKU ]");
        Optional<String> skuOpt = promptNonBlankString("Enter SKU: ", "Product SKU");
        if (skuOpt.isEmpty()) {
            return;
        }

        String sku = skuOpt.get();
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
                System.out.println("Product not found with SKU: " + sku);
            }
        } catch (ValidationException e) {
            System.out.println("Validation Error: " + e.getMessage());
        } catch (DatabaseException e) {
            System.out.println("Database Error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handleSearchProductsByName() {
        System.out.println("\n[ Search Products by Name ]");
        Optional<String> queryOpt = promptNonBlankString("Enter product name to search: ", "Search term");
        if (queryOpt.isEmpty()) {
            return;
        }

        String nameQuery = queryOpt.get();
        try {
            List<Product> products = productService.searchProductsByName(nameQuery);
            if (products.isEmpty()) {
                System.out.println("No products found matching: " + nameQuery);
                return;
            }

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
        } catch (DatabaseException e) {
            System.out.println("Database Error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handleUpdateProduct() {
        System.out.println("\n[ Update Product ]");
        Optional<String> skuOpt = promptNonBlankString("Enter SKU of product to update: ", "Product SKU");
        if (skuOpt.isEmpty()) {
            return;
        }

        String sku = skuOpt.get();
        Optional<Product> productOpt;
        try {
            productOpt = productService.findProductBySku(sku);
        } catch (ValidationException e) {
            System.out.println("Validation Error: " + e.getMessage());
            return;
        } catch (DatabaseException e) {
            System.out.println("Database Error: " + e.getMessage());
            return;
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            return;
        }

        if (productOpt.isEmpty()) {
            System.out.println("Product not found with SKU: " + sku);
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

        Optional<String> newNameOpt = promptNonBlankString("Enter new name: ", "Product name");
        if (newNameOpt.isEmpty()) {
            return;
        }

        Optional<BigDecimal> newPriceOpt = promptPrice("Enter new price: ");
        if (newPriceOpt.isEmpty()) {
            return;
        }

        Optional<Integer> newMinStockOpt = promptInteger("Enter new minimum stock: ", "Minimum stock", 0);
        if (newMinStockOpt.isEmpty()) {
            return;
        }

        try {
            productService.updateProduct(sku, newNameOpt.get(), newPriceOpt.get(), newMinStockOpt.get());
            System.out.println("Product updated successfully.");
        } catch (ValidationException e) {
            System.out.println("Validation Error: " + e.getMessage());
        } catch (DatabaseException e) {
            System.out.println("Database Error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handleDeleteProduct() {
        System.out.println("\n[ Delete Product ]");
        Optional<String> skuOpt = promptNonBlankString("Enter SKU of product to delete: ", "Product SKU");
        if (skuOpt.isEmpty()) {
            return;
        }

        String sku = skuOpt.get();
        Optional<Product> productOpt;
        try {
            productOpt = productService.findProductBySku(sku);
        } catch (ValidationException e) {
            System.out.println("Validation Error: " + e.getMessage());
            return;
        } catch (DatabaseException e) {
            System.out.println("Database Error: " + e.getMessage());
            return;
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            return;
        }

        if (productOpt.isEmpty()) {
            System.out.println("Product not found with SKU: " + sku);
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
                } catch (DatabaseException e) {
                    System.out.println("Database Error: " + e.getMessage());
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
        Optional<String> skuOpt = promptNonBlankString("Enter Product SKU: ", "Product SKU");
        if (skuOpt.isEmpty()) {
            return;
        }

        Optional<Integer> qtyOpt = promptInteger("Enter Quantity to Add: ", "Stock In quantity", 1);
        if (qtyOpt.isEmpty()) {
            return;
        }

        try {
            InventoryTransaction tx = productService.stockIn(skuOpt.get(), qtyOpt.get());
            System.out.println("Success: Stock In recorded successfully! Transaction ID: " + tx.getId());
        } catch (ValidationException e) {
            System.out.println("Validation Error: " + e.getMessage());
        } catch (DatabaseException e) {
            System.out.println("Database Error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handleStockOut() {
        System.out.println("\n[ Stock Out ]");
        Optional<String> skuOpt = promptNonBlankString("Enter Product SKU: ", "Product SKU");
        if (skuOpt.isEmpty()) {
            return;
        }

        Optional<Integer> qtyOpt = promptInteger("Enter Quantity to Remove: ", "Stock Out quantity", 1);
        if (qtyOpt.isEmpty()) {
            return;
        }

        try {
            InventoryTransaction tx = productService.stockOut(skuOpt.get(), qtyOpt.get());
            System.out.println("Success: Stock Out recorded successfully! Transaction ID: " + tx.getId());
        } catch (ValidationException e) {
            System.out.println("Validation Error: " + e.getMessage());
        } catch (DatabaseException e) {
            System.out.println("Database Error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handleViewHistory() {
        System.out.println("\n[ Transaction History ]");
        Optional<String> skuOpt = promptNonBlankString("Enter Product SKU: ", "Product SKU");
        if (skuOpt.isEmpty()) {
            return;
        }

        String sku = skuOpt.get();
        try {
            List<InventoryTransaction> transactions = productService.getTransactionHistory(sku);
            if (transactions.isEmpty()) {
                System.out.println("No transactions found for product SKU: " + sku);
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
        } catch (DatabaseException e) {
            System.out.println("Database Error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handleLowStockReport() {
        System.out.println("\n[ Low-Stock Report ]");
        try {
            List<Product> lowStockProducts = productService.getLowStockProducts();

            if (lowStockProducts.isEmpty()) {
                System.out.println("No low-stock products found.");
                return;
            }

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
        } catch (ValidationException e) {
            System.out.println("Validation Error: " + e.getMessage());
        } catch (DatabaseException e) {
            System.out.println("Database Error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
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
        } catch (ValidationException e) {
            System.out.println("Validation Error: " + e.getMessage());
        } catch (DatabaseException e) {
            System.out.println("Database Error: " + e.getMessage());
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
        } catch (ValidationException e) {
            System.out.println("Validation Error: " + e.getMessage());
        } catch (DatabaseException e) {
            System.out.println("Database Error: " + e.getMessage());
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

        SortField sortField = SortField.SKU;
        while (true) {
            Optional<String> fieldOpt = readLine("Enter choice [1-4, default 1]: ");
            if (fieldOpt.isEmpty()) {
                return;
            }
            String fieldInput = fieldOpt.get().trim();
            if (fieldInput.isEmpty() || "1".equals(fieldInput)) {
                sortField = SortField.SKU;
                break;
            } else if ("2".equals(fieldInput)) {
                sortField = SortField.NAME;
                break;
            } else if ("3".equals(fieldInput)) {
                sortField = SortField.PRICE;
                break;
            } else if ("4".equals(fieldInput)) {
                sortField = SortField.QUANTITY;
                break;
            } else {
                System.out.println("Invalid choice. Please enter 1, 2, 3, 4, or press Enter for default.");
            }
        }

        System.out.println("Select sort direction:");
        System.out.println("1. Ascending (default)");
        System.out.println("2. Descending");

        SortDirection sortDirection = SortDirection.ASC;
        while (true) {
            Optional<String> dirOpt = readLine("Enter choice [1-2, default 1]: ");
            if (dirOpt.isEmpty()) {
                return;
            }
            String dirInput = dirOpt.get().trim();
            if (dirInput.isEmpty() || "1".equals(dirInput)) {
                sortDirection = SortDirection.ASC;
                break;
            } else if ("2".equals(dirInput)) {
                sortDirection = SortDirection.DESC;
                break;
            } else {
                System.out.println("Invalid choice. Please enter 1, 2, or press Enter for default.");
            }
        }

        Optional<Integer> pageSizeOpt = promptOptionalInteger("Enter page size [default 5]: ", "Page size", 5, 1);
        if (pageSizeOpt.isEmpty()) {
            return;
        }
        int pageSize = pageSizeOpt.get();

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
                if (!scanner.hasNextLine()) {
                    break;
                }
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
                        int totalPages = result.getTotalPages();
                        while (true) {
                            Optional<String> pageInputOpt = readLine("Enter page number (1-" + totalPages + "): ");
                            if (pageInputOpt.isEmpty()) {
                                break;
                            }
                            String pageInput = pageInputOpt.get().trim();
                            try {
                                int targetPage = Integer.parseInt(pageInput);
                                if (targetPage >= 1 && targetPage <= totalPages) {
                                    currentPage = targetPage;
                                    break;
                                } else {
                                    System.out.println("Error: Page number must be between 1 and " + totalPages + ". Please try again.");
                                }
                            } catch (NumberFormatException e) {
                                System.out.println("Error: Page number must be a valid whole number. Please try again.");
                            }
                        }
                    }
                    case "B" -> browsing = false;
                    default -> System.out.println("Invalid option. Please enter N, P, J, or B.");
                }
            } catch (ValidationException e) {
                System.out.println("Validation Error: " + e.getMessage());
                browsing = false;
            } catch (DatabaseException e) {
                System.out.println("Database Error: " + e.getMessage());
                browsing = false;
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
                browsing = false;
            }
        }
    }

    // --- Reusable Console Input Helpers ---

    private Optional<String> readLine(String prompt) {
        System.out.print(prompt);
        if (!scanner.hasNextLine()) {
            return Optional.empty();
        }
        return Optional.of(scanner.nextLine());
    }

    private Optional<String> promptNonBlankString(String prompt, String fieldName) {
        while (true) {
            Optional<String> lineOpt = readLine(prompt);
            if (lineOpt.isEmpty()) {
                return Optional.empty();
            }
            String input = lineOpt.get().trim();
            if (!input.isEmpty()) {
                return Optional.of(input);
            }
            System.out.println("Error: " + fieldName + " cannot be blank. Please try again.");
        }
    }

    private Optional<BigDecimal> promptPrice(String prompt) {
        while (true) {
            Optional<String> lineOpt = readLine(prompt);
            if (lineOpt.isEmpty()) {
                return Optional.empty();
            }
            String input = lineOpt.get().trim();
            if (input.isEmpty()) {
                System.out.println("Error: Price cannot be blank. Please try again.");
                continue;
            }
            try {
                BigDecimal price = new BigDecimal(input);
                if (price.compareTo(BigDecimal.ZERO) < 0) {
                    System.out.println("Error: Price must be greater than or equal to 0. Please try again.");
                    continue;
                }
                return Optional.of(price);
            } catch (NumberFormatException e) {
                System.out.println("Error: Price must be a valid number (e.g. 19.99). Please try again.");
            }
        }
    }

    private Optional<Integer> promptInteger(String prompt, String fieldName, int minAllowed) {
        while (true) {
            Optional<String> lineOpt = readLine(prompt);
            if (lineOpt.isEmpty()) {
                return Optional.empty();
            }
            String input = lineOpt.get().trim();
            if (input.isEmpty()) {
                System.out.println("Error: " + fieldName + " cannot be blank. Please try again.");
                continue;
            }
            try {
                int value = Integer.parseInt(input);
                if (value < minAllowed) {
                    if (minAllowed == 1) {
                        System.out.println("Error: " + fieldName + " must be greater than zero. Please try again.");
                    } else {
                        System.out.println("Error: " + fieldName + " must be greater than or equal to " + minAllowed + ". Please try again.");
                    }
                    continue;
                }
                return Optional.of(value);
            } catch (NumberFormatException e) {
                System.out.println("Error: " + fieldName + " must be a valid whole number. Please try again.");
            }
        }
    }

    private Optional<Integer> promptOptionalInteger(String prompt, String fieldName, int defaultValue, int minAllowed) {
        while (true) {
            Optional<String> lineOpt = readLine(prompt);
            if (lineOpt.isEmpty()) {
                return Optional.empty();
            }
            String input = lineOpt.get().trim();
            if (input.isEmpty()) {
                return Optional.of(defaultValue);
            }
            try {
                int value = Integer.parseInt(input);
                if (value < minAllowed) {
                    if (minAllowed == 1) {
                        System.out.println("Error: " + fieldName + " must be greater than zero. Please try again.");
                    } else {
                        System.out.println("Error: " + fieldName + " must be greater than or equal to " + minAllowed + ". Please try again.");
                    }
                    continue;
                }
                return Optional.of(value);
            } catch (NumberFormatException e) {
                System.out.println("Error: " + fieldName + " must be a valid whole number. Please try again.");
            }
        }
    }
}
