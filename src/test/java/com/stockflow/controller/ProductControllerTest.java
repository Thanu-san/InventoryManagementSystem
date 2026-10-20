package com.stockflow.controller;

import com.stockflow.exception.DatabaseException;
import com.stockflow.model.Product;
import com.stockflow.model.Supplier;
import com.stockflow.repository.InMemoryInventoryTransactionRepository;
import com.stockflow.repository.InMemoryProductRepository;
import com.stockflow.repository.InMemorySupplierRepository;
import com.stockflow.repository.ProductRepository;
import com.stockflow.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProductController Console Input & Error Recovery Tests")
class ProductControllerTest {

    private InMemoryProductRepository productRepository;
    private InMemoryInventoryTransactionRepository transactionRepository;
    private InMemorySupplierRepository supplierRepository;
    private ProductService productService;
    private ByteArrayOutputStream outputCapture;

    @BeforeEach
    void setUp() {
        productRepository = new InMemoryProductRepository();
        transactionRepository = new InMemoryInventoryTransactionRepository(productRepository);
        supplierRepository = new InMemorySupplierRepository();
        productService = new ProductService(productRepository, transactionRepository, supplierRepository);
        outputCapture = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputCapture, true, StandardCharsets.UTF_8));
    }

    private ProductController createController(String simulatedInput) {
        Scanner scanner = new Scanner(simulatedInput);
        return new ProductController(productService, scanner);
    }

    private String getConsoleOutput() {
        return outputCapture.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Should retry when entering invalid price then negative price until valid price entered on Add Product")
    void shouldRetryWhenEnteringInvalidPriceThenValidPriceOnAddProduct() {
        // Menu choice: 1 (Add Product), then 16 (Exit)
        // SKU: "KB-01", Name: "Keychron", Price: "not-a-number" -> "-5.00" -> "89.99", Qty: "10", MinStock: "2"
        String input = String.join("\n",
                "1",
                "KB-01",
                "Keychron",
                "not-a-number",
                "-5.00",
                "89.99",
                "10",
                "2",
                "16"
        ) + "\n";

        ProductController controller = createController(input);
        controller.runMenu();

        Optional<Product> createdOpt = productRepository.findBySku("KB-01");
        assertTrue(createdOpt.isPresent(), "Product should have been created");
        Product p = createdOpt.get();
        assertEquals("KB-01", p.getSku());
        assertEquals("Keychron", p.getName());
        assertEquals(new BigDecimal("89.99"), p.getPrice());
        assertEquals(10, p.getQuantity());

        String output = getConsoleOutput();
        assertTrue(output.contains("Error: Price must be a valid number"));
        assertTrue(output.contains("Error: Price must be greater than or equal to 0"));
        assertTrue(output.contains("Success: Product created successfully!"));
    }

    @Test
    @DisplayName("Should retry when entering blank or invalid quantity on Add Product")
    void shouldRetryWhenEnteringBlankOrInvalidQuantityOnAddProduct() {
        // SKU: "MS-01", Name: "Mouse", Price: "25.00", Qty: "" -> "abc" -> "-3" -> "15", MinStock: "3"
        String input = String.join("\n",
                "1",
                "MS-01",
                "Mouse",
                "25.00",
                "   ",
                "abc",
                "-3",
                "15",
                "3",
                "16"
        ) + "\n";

        ProductController controller = createController(input);
        controller.runMenu();

        Optional<Product> createdOpt = productRepository.findBySku("MS-01");
        assertTrue(createdOpt.isPresent());
        assertEquals(15, createdOpt.get().getQuantity());

        String output = getConsoleOutput();
        assertTrue(output.contains("Error: Quantity cannot be blank. Please try again."));
        assertTrue(output.contains("Error: Quantity must be a valid whole number. Please try again."));
        assertTrue(output.contains("Error: Quantity must be greater than or equal to 0. Please try again."));
        assertTrue(output.contains("Success: Product created successfully!"));
    }

    @Test
    @DisplayName("Should retry when entering blank SKU on Add Product")
    void shouldRetryWhenEnteringBlankSkuOnAddProduct() {
        String input = String.join("\n",
                "1",
                "   ",
                "VALID-SKU",
                "Desk Mat",
                "19.99",
                "50",
                "5",
                "16"
        ) + "\n";

        ProductController controller = createController(input);
        controller.runMenu();

        assertTrue(productRepository.existsBySku("VALID-SKU"));
        String output = getConsoleOutput();
        assertTrue(output.contains("Error: SKU cannot be blank. Please try again."));
        assertTrue(output.contains("Success: Product created successfully!"));
    }

    @Test
    @DisplayName("Should handle duplicate SKU gracefully without crashing on Add Product")
    void shouldHandleDuplicateSkuGracefullyWithoutCrashingOnAddProduct() {
        productRepository.save(new Product("DUP-SKU", "Existing Desk", new BigDecimal("150.00"), 5, 1));

        String input = String.join("\n",
                "1",
                "DUP-SKU",
                "Another Desk",
                "199.99",
                "2",
                "1",
                "16"
        ) + "\n";

        ProductController controller = createController(input);
        controller.runMenu();

        String output = getConsoleOutput();
        assertTrue(output.contains("Validation Error: Product with SKU 'DUP-SKU' already exists."));
    }

    @Test
    @DisplayName("Should retry on non-positive or non-numeric stock in quantity")
    void shouldRetryOnInvalidStockInQuantity() {
        productRepository.save(new Product("STOCK-IN-SKU", "Monitor", new BigDecimal("250.00"), 5, 2));

        // Option 6 (Stock In): SKU "STOCK-IN-SKU", Qty: "abc" -> "0" -> "-5" -> "10", then 16
        String input = String.join("\n",
                "6",
                "STOCK-IN-SKU",
                "abc",
                "0",
                "-5",
                "10",
                "16"
        ) + "\n";

        ProductController controller = createController(input);
        controller.runMenu();

        Product updated = productRepository.findBySku("STOCK-IN-SKU").orElseThrow();
        assertEquals(15, updated.getQuantity());

        String output = getConsoleOutput();
        assertTrue(output.contains("Error: Stock In quantity must be a valid whole number."));
        assertTrue(output.contains("Error: Stock In quantity must be greater than zero."));
        assertTrue(output.contains("Success: Stock In recorded successfully!"));
    }

    @Test
    @DisplayName("Should handle insufficient stock in Stock Out gracefully without crashing")
    void shouldHandleInsufficientStockGracefullyWithoutCrashing() {
        productRepository.save(new Product("STOCK-OUT-SKU", "Chair", new BigDecimal("120.00"), 3, 1));

        // Option 7 (Stock Out): SKU "STOCK-OUT-SKU", Qty: "10" (exceeds 3), then 16
        String input = String.join("\n",
                "7",
                "STOCK-OUT-SKU",
                "10",
                "16"
        ) + "\n";

        ProductController controller = createController(input);
        controller.runMenu();

        Product untouched = productRepository.findBySku("STOCK-OUT-SKU").orElseThrow();
        assertEquals(3, untouched.getQuantity(), "Quantity must remain unchanged");

        String output = getConsoleOutput();
        assertTrue(output.contains("Validation Error: Insufficient stock for product 'STOCK-OUT-SKU'. Available: 3, Requested: 10"));
    }

    @Test
    @DisplayName("Should handle invalid menu choices followed by valid menu choice")
    void shouldHandleInvalidMenuChoiceFollowedByValidChoice() {
        String input = String.join("\n",
                "99",
                "invalid-choice",
                "16"
        ) + "\n";

        ProductController controller = createController(input);
        controller.runMenu();

        String output = getConsoleOutput();
        assertTrue(output.contains("Invalid option. Please try again."));
    }

    @Test
    @DisplayName("Should handle browsing with invalid sort field, direction, and page size with retry")
    void shouldHandleBrowseProductsInvalidChoicesAndPageSizeWithRetry() {
        productRepository.save(new Product("BR-01", "Browse Alpha", new BigDecimal("10.00"), 5, 1));
        productRepository.save(new Product("BR-02", "Browse Beta", new BigDecimal("20.00"), 10, 2));

        // Option 13 (Browse)
        // Sort field: "9" (invalid) -> "2" (Name)
        // Direction: "9" (invalid) -> "1" (ASC)
        // Page size: "-2" (invalid) -> "abc" (invalid) -> "5" (valid)
        // Navigation: "B" (Back)
        // Exit menu: 16
        String input = String.join("\n",
                "13",
                "9",
                "2",
                "9",
                "1",
                "-2",
                "abc",
                "5",
                "B",
                "16"
        ) + "\n";

        ProductController controller = createController(input);
        controller.runMenu();

        String output = getConsoleOutput();
        assertTrue(output.contains("Invalid choice. Please enter 1, 2, 3, 4, or press Enter for default."));
        assertTrue(output.contains("Invalid choice. Please enter 1, 2, or press Enter for default."));
        assertTrue(output.contains("Error: Page size must be greater than zero. Please try again."));
        assertTrue(output.contains("Error: Page size must be a valid whole number. Please try again."));
        assertTrue(output.contains("Page 1 of 1 (Total Products: 2)"));
    }

    @Test
    @DisplayName("Should handle Update Product with retry on invalid price")
    void shouldHandleUpdateProductWithRetryOnInvalidPrice() {
        productRepository.save(new Product("UP-SKU", "Old Name", new BigDecimal("50.00"), 10, 2));

        // Option 4 (Update Product)
        // SKU: "UP-SKU", New name: "New Name", Price: "bad-price" -> "75.00", MinStock: "4"
        String input = String.join("\n",
                "4",
                "UP-SKU",
                "New Name",
                "bad-price",
                "75.00",
                "4",
                "16"
        ) + "\n";

        ProductController controller = createController(input);
        controller.runMenu();

        Product updated = productRepository.findBySku("UP-SKU").orElseThrow();
        assertEquals("New Name", updated.getName());
        assertEquals(new BigDecimal("75.00"), updated.getPrice());
        assertEquals(4, updated.getMinimumStock());

        String output = getConsoleOutput();
        assertTrue(output.contains("Error: Price must be a valid number (e.g. 19.99). Please try again."));
        assertTrue(output.contains("Product updated successfully."));
    }

    @Test
    @DisplayName("Should handle Delete confirmation cancel and retry")
    void shouldHandleDeleteConfirmationCancelAndConfirm() {
        productRepository.save(new Product("DEL-SKU", "To Delete", new BigDecimal("30.00"), 5, 1));

        // Option 5 (Delete): SKU: "DEL-SKU", confirm: "invalid" -> "n" (cancelled)
        // Option 5 (Delete again): SKU: "DEL-SKU", confirm: "y" (deleted)
        String input = String.join("\n",
                "5",
                "DEL-SKU",
                "maybe",
                "n",
                "5",
                "DEL-SKU",
                "y",
                "16"
        ) + "\n";

        ProductController controller = createController(input);
        controller.runMenu();

        assertFalse(productRepository.existsBySku("DEL-SKU"), "Product should have been deleted on second attempt");

        String output = getConsoleOutput();
        assertTrue(output.contains("Please enter Y or N."));
        assertTrue(output.contains("Delete cancelled."));
        assertTrue(output.contains("Product deleted successfully."));
    }

    @Test
    @DisplayName("Should catch DatabaseException and display user-friendly message without crashing")
    void shouldCatchDatabaseExceptionAndDisplayUserFriendlyMessage() {
        // Create a controller with a throwing service
        ProductRepository throwingRepo = new InMemoryProductRepository() {
            @Override
            public Optional<Product> findBySku(String sku) {
                throw new DatabaseException("Simulated connection timeout to database");
            }
        };
        ProductService failingService = new ProductService(throwingRepo);

        String input = String.join("\n",
                "3",
                "FAIL-SKU",
                "16"
        ) + "\n";

        ProductController controller = new ProductController(failingService, new Scanner(input));
        assertDoesNotThrow(controller::runMenu, "Controller should never let DatabaseException crash the application");

        String output = getConsoleOutput();
        assertTrue(output.contains("Database Error: Simulated connection timeout to database"));
    }

    @Test
    @DisplayName("Should assign supplier to product via menu option 14")
    void shouldAssignSupplierViaMenuOption14() {
        productRepository.save(new Product("ASSIGN-SKU", "Wireless Earbuds", new BigDecimal("49.99"), 20, 5));
        supplierRepository.save(new Supplier("SUP-AUDIO", "AudioTech Inc", "Alice", "555-1111", "alice@audiotech.com", "Tokyo"));

        String input = String.join("\n",
                "14",
                "ASSIGN-SKU",
                "SUP-AUDIO",
                "16"
        ) + "\n";

        ProductController controller = createController(input);
        controller.runMenu();

        Product updated = productRepository.findBySku("ASSIGN-SKU").orElseThrow();
        assertNotNull(updated.getSupplierId());

        String output = getConsoleOutput();
        assertTrue(output.contains("Success: Supplier 'SUP-AUDIO' (AudioTech Inc) assigned to product 'ASSIGN-SKU' successfully!"));
    }

    @Test
    @DisplayName("Should remove supplier from product via menu option 15 with confirmation")
    void shouldRemoveSupplierViaMenuOption15WithConfirmation() {
        Supplier s = supplierRepository.save(new Supplier("SUP-REMOVE", "RemoveTech", null, null, null, null));
        Product p = new Product("REMOVE-SKU", "Device", new BigDecimal("99.00"), 10, 2);
        p.setSupplierId(s.getId());
        productRepository.save(p);

        String input = String.join("\n",
                "15",
                "REMOVE-SKU",
                "y",
                "16"
        ) + "\n";

        ProductController controller = createController(input);
        controller.runMenu();

        Product updated = productRepository.findBySku("REMOVE-SKU").orElseThrow();
        assertNull(updated.getSupplierId());

        String output = getConsoleOutput();
        assertTrue(output.contains("Success: Supplier assignment removed from product 'REMOVE-SKU' successfully."));
    }

    @Test
    @DisplayName("Should cancel supplier removal when entering 'n'")
    void shouldCancelSupplierRemovalWhenEnteringN() {
        Supplier s = supplierRepository.save(new Supplier("SUP-KEEP", "KeepTech", null, null, null, null));
        Product p = new Product("KEEP-SKU", "Device", new BigDecimal("99.00"), 10, 2);
        p.setSupplierId(s.getId());
        productRepository.save(p);

        String input = String.join("\n",
                "15",
                "KEEP-SKU",
                "n",
                "16"
        ) + "\n";

        ProductController controller = createController(input);
        controller.runMenu();

        Product updated = productRepository.findBySku("KEEP-SKU").orElseThrow();
        assertEquals(s.getId(), updated.getSupplierId(), "Supplier ID should remain assigned");

        String output = getConsoleOutput();
        assertTrue(output.contains("Removal cancelled."));
    }

    @Test
    @DisplayName("Should display assigned supplier when finding product by SKU via menu option 3")
    void shouldDisplayAssignedSupplierWhenFindingProductBySku() {
        Supplier s = supplierRepository.save(new Supplier("SUP-FIND", "FindSupplier", null, null, null, null));
        Product p = new Product("FIND-SKU", "Widget", new BigDecimal("10.00"), 5, 1);
        p.setSupplierId(s.getId());
        productRepository.save(p);

        String input = String.join("\n",
                "3",
                "FIND-SKU",
                "16"
        ) + "\n";

        ProductController controller = createController(input);
        controller.runMenu();

        String output = getConsoleOutput();
        assertTrue(output.contains("Assigned Supplier: [SUP-FIND] FindSupplier"));
    }
}
