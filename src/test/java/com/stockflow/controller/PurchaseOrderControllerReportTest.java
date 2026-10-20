package com.stockflow.controller;

import com.stockflow.model.Product;
import com.stockflow.model.PurchaseOrder;
import com.stockflow.model.ReceiptLineInput;
import com.stockflow.model.Supplier;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PurchaseOrderController Report Console Tests")
class PurchaseOrderControllerReportTest {

    private InMemoryPurchaseOrderRepository poRepository;
    private InMemorySupplierRepository supplierRepository;
    private InMemoryProductRepository productRepository;
    private InMemoryInventoryTransactionRepository transactionRepository;
    private InMemoryPurchaseOrderReceiptRepository receiptRepository;
    private InMemoryPurchaseOrderReportRepository reportRepository;
    private InMemorySupplierReturnRepository returnRepository;
    private PurchaseOrderService poService;

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

        supplierRepository.save(new Supplier("SUP-100", "Acme Supplies", null, null, null, null));
        productRepository.save(new Product("SKU-100", "Widget", new BigDecimal("10.00"), 50, 5));
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
    @DisplayName("Should navigate to reports submenu and return to previous menu")
    void shouldNavigateToReportsMenuAndReturn() {
        String input = String.join("\n",
                "11", // Open Reports Submenu
                "4",  // Back to Purchase Order Menu
                "12"  // Back to Main Menu
        ) + "\n";

        String output = runControllerWithInput(input);
        assertTrue(output.contains("--- Purchase Order Reports ---"));
        assertTrue(output.contains("1. View Outstanding Purchase Orders"));
        assertTrue(output.contains("2. View Receiving History for Date Range"));
        assertTrue(output.contains("3. View Supplier Purchasing Summary"));
        assertTrue(output.contains("4. Back to Purchase Order Menu"));
    }

    @Test
    @DisplayName("Should display message when no outstanding purchase orders exist")
    void shouldDisplayNoOutstandingOrdersWhenEmpty() {
        String input = String.join("\n",
                "11", // Open Reports Submenu
                "1",  // Outstanding Purchase Orders
                "4",  // Back
                "12"  // Exit
        ) + "\n";

        String output = runControllerWithInput(input);
        assertTrue(output.contains("[ Outstanding Purchase Orders Report ]"));
        assertTrue(output.contains("No outstanding purchase orders found."));
    }

    @Test
    @DisplayName("Should display outstanding purchase orders table when orders exist")
    void shouldDisplayOutstandingOrdersTable() {
        poService.createDraftOrder("PO-OUT-1", "SUP-100");
        poService.addOrUpdateItem("PO-OUT-1", "SKU-100", 20, new BigDecimal("12.00"));
        poService.placeOrder("PO-OUT-1");

        String input = String.join("\n",
                "11", // Reports
                "1",  // Outstanding
                "4",  // Back
                "12"  // Exit
        ) + "\n";

        String output = runControllerWithInput(input);
        assertTrue(output.contains("[ Outstanding Purchase Orders Report ]"));
        assertTrue(output.contains("PO-OUT-1"));
        assertTrue(output.contains("SUP-100"));
        assertTrue(output.contains("Acme Supplies"));
        assertTrue(output.contains("Summary: 1 order(s)"));
    }

    @Test
    @DisplayName("Should prompt for dates and view receiving history report")
    void shouldViewReceivingHistoryReport() {
        poService.createDraftOrder("PO-RECV-RPT", "SUP-100");
        poService.addOrUpdateItem("PO-RECV-RPT", "SKU-100", 15, new BigDecimal("8.50"));
        poService.placeOrder("PO-RECV-RPT");

        PurchaseOrder order = poService.getOrderByNumber("PO-RECV-RPT").orElseThrow();
        Long itemId = order.getItems().get(0).getId();
        poService.receiveGoods("PO-RECV-RPT", List.of(new ReceiptLineInput(itemId, 5)), "Shipment A");

        LocalDate today = LocalDate.now();
        String startStr = today.minusDays(1).toString();
        String endStr = today.plusDays(1).toString();

        String input = String.join("\n",
                "11",      // Reports
                "2",       // Receiving History
                startStr,  // Start date
                endStr,    // End date
                "4",       // Back
                "12"       // Exit
        ) + "\n";

        String output = runControllerWithInput(input);
        assertTrue(output.contains("[ Receiving History Report ]"));
        assertTrue(output.contains("PO-RECV-RPT"));
        assertTrue(output.contains("Acme Supplies"));
        assertTrue(output.contains("SKU-100"));
        assertTrue(output.contains("$8.50"));
        assertTrue(output.contains("$42.50"));
        assertTrue(output.contains("Summary: 1 receipt line(s)"));
    }

    @Test
    @DisplayName("Should handle invalid date formats and start date after end date")
    void shouldHandleInvalidDatesGracefully() {
        String input = String.join("\n",
                "11",           // Reports
                "2",            // Receiving History
                "not-a-date",   // Invalid date format
                "2026-10-25",   // Valid start date
                "2026-10-20",   // End date earlier than start date!
                "4",            // Back
                "12"            // Exit
        ) + "\n";

        String output = runControllerWithInput(input);
        assertTrue(output.contains("Error: Invalid date format: 'not-a-date'"));
        assertTrue(output.contains("Validation Error: Start date cannot be after end date."));
    }

    @Test
    @DisplayName("Should display supplier purchasing summary report")
    void shouldDisplaySupplierPurchasingSummaryReport() {
        poService.createDraftOrder("PO-SUP-SUMM", "SUP-100");
        poService.addOrUpdateItem("PO-SUP-SUMM", "SKU-100", 10, new BigDecimal("15.00"));
        poService.placeOrder("PO-SUP-SUMM");

        String input = String.join("\n",
                "11", // Reports
                "3",  // Supplier Purchasing Summary
                "4",  // Back
                "12"  // Exit
        ) + "\n";

        String output = runControllerWithInput(input);
        assertTrue(output.contains("[ Supplier Purchasing Summary ]"));
        assertTrue(output.contains("SUP-100"));
        assertTrue(output.contains("Acme Supplies"));
        assertTrue(output.contains("$150.00"));
        assertTrue(output.contains("Note: PO Value reflects committed orders (PLACED, PARTIALLY_RECEIVED, RECEIVED), not payments made."));
    }

    @Test
    @DisplayName("Should handle invalid choices in reports menu")
    void shouldHandleInvalidReportsMenuChoice() {
        String input = String.join("\n",
                "11", // Reports
                "99", // Invalid choice
                "4",  // Back
                "12"  // Exit
        ) + "\n";

        String output = runControllerWithInput(input);
        assertTrue(output.contains("Invalid option. Please try again."));
    }
}
