package com.stockflow.controller;

import com.stockflow.model.Product;
import com.stockflow.model.PurchaseOrder;
import com.stockflow.model.PurchaseOrderItem;
import com.stockflow.model.ReceiptLineInput;
import com.stockflow.model.ReturnLineInput;
import com.stockflow.model.Supplier;
import com.stockflow.model.SupplierReturn;
import com.stockflow.repository.InMemoryInventoryTransactionRepository;
import com.stockflow.repository.InMemoryProductRepository;
import com.stockflow.repository.InMemoryPurchaseOrderReceiptRepository;
import com.stockflow.repository.InMemoryPurchaseOrderReportRepository;
import com.stockflow.repository.InMemoryPurchaseOrderRepository;
import com.stockflow.repository.InMemorySupplierRepository;
import com.stockflow.repository.InMemorySupplierReturnRepository;
import com.stockflow.service.PurchaseOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PurchaseOrderController Return Console Tests")
class PurchaseOrderControllerReturnTest {

    private InMemoryPurchaseOrderRepository poRepository;
    private InMemorySupplierRepository supplierRepository;
    private InMemoryProductRepository productRepository;
    private InMemoryInventoryTransactionRepository transactionRepository;
    private InMemoryPurchaseOrderReceiptRepository receiptRepository;
    private InMemoryPurchaseOrderReportRepository reportRepository;
    private InMemorySupplierReturnRepository returnRepository;
    private PurchaseOrderService poService;

    private Supplier testSupplier;
    private Product testProduct;
    private PurchaseOrder testOrder;
    private long receiptItemId;

    @BeforeEach
    void setUp() {
        poRepository = new InMemoryPurchaseOrderRepository();
        supplierRepository = new InMemorySupplierRepository();
        productRepository = new InMemoryProductRepository();
        transactionRepository = new InMemoryInventoryTransactionRepository(productRepository);
        receiptRepository = new InMemoryPurchaseOrderReceiptRepository(poRepository, productRepository, transactionRepository);
        reportRepository = new InMemoryPurchaseOrderReportRepository(poRepository, supplierRepository, receiptRepository);
        returnRepository = new InMemorySupplierReturnRepository(poRepository, receiptRepository, productRepository, transactionRepository);
        poService = new PurchaseOrderService(poRepository, supplierRepository, productRepository, receiptRepository, reportRepository, returnRepository);

        testSupplier = supplierRepository.save(new Supplier("SUP-CTRL-1", "Test Returns Co", null, null, null, null));
        testProduct = productRepository.save(new Product("SKU-CTRL-1", "Widget", new BigDecimal("10.00"), 50, 5));

        testOrder = poService.createDraftOrder("PO-RET-CTRL-1", "SUP-CTRL-1");
        PurchaseOrderItem item = poService.addOrUpdateItem("PO-RET-CTRL-1", "SKU-CTRL-1", 10, new BigDecimal("8.00"));
        poService.placeOrder("PO-RET-CTRL-1");

        var receipt = poService.receiveGoods("PO-RET-CTRL-1", List.of(
                new ReceiptLineInput(item.getId(), 10)
        ), "Initial delivery");
        receiptItemId = receipt.getItems().get(0).getId();
    }

    private String runControllerWithInput(String input) {
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            PurchaseOrderController controller = new PurchaseOrderController(poService, new Scanner(in));
            controller.runMenu();
        } finally {
            System.setOut(originalOut);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Should navigate to Supplier Returns submenu and return to PO menu")
    void shouldNavigateToReturnsSubmenuAndReturn() {
        String input = String.join("\n",
                "10", // Supplier Returns submenu
                "4",  // Back to Purchase Order Menu
                "12"  // Back to Main Menu
        ) + "\n";

        String output = runControllerWithInput(input);
        assertTrue(output.contains("--- Supplier Returns ---"));
        assertTrue(output.contains("Purchase Order Management"));
    }

    @Test
    @DisplayName("Should create return successfully via console")
    void shouldCreateReturnSuccessfully() {
        String input = String.join("\n",
                "10",               // Submenu
                "1",                // Create Supplier Return
                "PO-RET-CTRL-1",    // Order number
                String.valueOf(receiptItemId), // Line ID
                "3",                // Quantity to return
                "Damaged packaging",// Reason
                "Y",                // Confirm?
                "4",                // Back to PO menu
                "12"                // Exit
        ) + "\n";

        String output = runControllerWithInput(input);
        assertTrue(output.contains("Success: Supplier return recorded successfully!"));
        assertTrue(output.contains("Return Number:"));
        assertTrue(output.contains("STOCK_OUT transaction created"));

        // Verify product stock reduced from 60 (50+10) to 57
        Product updated = productRepository.findBySku(testProduct.getSku()).orElseThrow();
        assertEquals(57, updated.getQuantity());
    }

    @Test
    @DisplayName("Should cancel return creation when user declines confirmation")
    void shouldCancelReturnCreationOnDecline() {
        String input = String.join("\n",
                "10",
                "1",
                "PO-RET-CTRL-1",
                String.valueOf(receiptItemId),
                "2",
                "Wrong item",
                "N",                // Decline confirmation
                "4",
                "12"
        ) + "\n";

        String output = runControllerWithInput(input);
        assertTrue(output.contains("Return cancelled."));

        // Verify no change to stock
        Product updated = productRepository.findBySku(testProduct.getSku()).orElseThrow();
        assertEquals(60, updated.getQuantity());
    }

    @Test
    @DisplayName("Should view return history and details")
    void shouldViewReturnHistoryAndDetails() {
        SupplierReturn ret = poService.createSupplierReturn("PO-RET-CTRL-1", List.of(
                new ReturnLineInput(receiptItemId, 2, "Faulty sensor")
        ), "Defective goods");

        String input = String.join("\n",
                "10",               // Submenu
                "2",                // View Return History
                "PO-RET-CTRL-1",    // Order number
                "3",                // View Return Details
                ret.getReturnNumber(), // Return number
                "4",                // Back to PO menu
                "12"                // Exit
        ) + "\n";

        String output = runControllerWithInput(input);
        assertTrue(output.contains("Return History for Order: PO-RET-CTRL-1"));
        assertTrue(output.contains(ret.getReturnNumber()));
        assertTrue(output.contains("Supplier Return Details"));
        assertTrue(output.contains("Faulty sensor"));
    }

    @Test
    @DisplayName("Should handle viewing non-existent return details gracefully")
    void shouldHandleNonExistentReturnGracefully() {
        String input = String.join("\n",
                "10",
                "3",
                "NON-EXISTENT-RET",
                "4",
                "12"
        ) + "\n";

        String output = runControllerWithInput(input);
        assertTrue(output.contains("Supplier return not found with return number: NON-EXISTENT-RET"));
    }
}
