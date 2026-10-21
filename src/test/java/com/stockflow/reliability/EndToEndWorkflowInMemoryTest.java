package com.stockflow.reliability;

import com.stockflow.model.InventorySummary;
import com.stockflow.model.InventoryTransaction;
import com.stockflow.model.OutstandingPurchaseOrderReportItem;
import com.stockflow.model.Product;
import com.stockflow.model.PurchaseOrder;
import com.stockflow.model.PurchaseOrderItem;
import com.stockflow.model.PurchaseOrderReceipt;
import com.stockflow.model.PurchaseOrderStatus;
import com.stockflow.model.ReceiptLineInput;
import com.stockflow.model.ReceivingHistoryReportItem;
import com.stockflow.model.ReturnLineInput;
import com.stockflow.model.Supplier;
import com.stockflow.model.SupplierPurchasingSummaryItem;
import com.stockflow.model.SupplierReturn;
import com.stockflow.model.TransactionType;
import com.stockflow.repository.InMemoryInventoryTransactionRepository;
import com.stockflow.repository.InMemoryProductRepository;
import com.stockflow.repository.InMemoryPurchaseOrderReceiptRepository;
import com.stockflow.repository.InMemoryPurchaseOrderReportRepository;
import com.stockflow.repository.InMemoryPurchaseOrderRepository;
import com.stockflow.repository.InMemorySupplierRepository;
import com.stockflow.repository.InMemorySupplierReturnRepository;
import com.stockflow.service.ProductService;
import com.stockflow.service.PurchaseOrderService;
import com.stockflow.service.SupplierService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 16: Complete End-to-End Workflow Test (In-Memory)")
class EndToEndWorkflowInMemoryTest {

    private InMemoryProductRepository productRepository;
    private InMemorySupplierRepository supplierRepository;
    private InMemoryInventoryTransactionRepository transactionRepository;
    private InMemoryPurchaseOrderRepository poRepository;
    private InMemoryPurchaseOrderReceiptRepository receiptRepository;
    private InMemoryPurchaseOrderReportRepository reportRepository;
    private InMemorySupplierReturnRepository returnRepository;

    private ProductService productService;
    private SupplierService supplierService;
    private PurchaseOrderService poService;

    @BeforeEach
    void setUp() {
        productRepository = new InMemoryProductRepository();
        supplierRepository = new InMemorySupplierRepository();
        transactionRepository = new InMemoryInventoryTransactionRepository(productRepository);
        poRepository = new InMemoryPurchaseOrderRepository();
        receiptRepository = new InMemoryPurchaseOrderReceiptRepository(poRepository, productRepository, transactionRepository);
        reportRepository = new InMemoryPurchaseOrderReportRepository(poRepository, supplierRepository, receiptRepository);
        returnRepository = new InMemorySupplierReturnRepository(poRepository, receiptRepository, productRepository, transactionRepository);

        productService = new ProductService(productRepository, transactionRepository, supplierRepository, poRepository);
        supplierService = new SupplierService(supplierRepository, productRepository, poRepository);
        poService = new PurchaseOrderService(poRepository, supplierRepository, productRepository, receiptRepository, reportRepository, returnRepository);
    }

    @Test
    @DisplayName("Complete 14-Step Warehouse Lifecycle Workflow")
    void shouldExecuteCompleteWarehouseWorkflowSuccessfully() {
        // Step 1: Create a supplier
        Supplier supplier = supplierService.createSupplier("SUP-E2E-1", "Apex Logistics Ltd", "Alice Smith", "555-0199", "alice@apex.com", "100 Warehouse Way");
        assertNotNull(supplier.getId());

        // Step 2: Create two products
        Product prodA = productService.createProduct("SKU-E2E-A", "Industrial Drill", new BigDecimal("120.00"), 10, 5);
        Product prodB = productService.createProduct("SKU-E2E-B", "Safety Goggles", new BigDecimal("15.00"), 50, 10);
        assertEquals(10, prodA.getQuantity());
        assertEquals(50, prodB.getQuantity());

        // Step 3: Assign the supplier to the products
        productService.assignSupplier("SKU-E2E-A", "SUP-E2E-1");
        productService.assignSupplier("SKU-E2E-B", "SUP-E2E-1");
        assertEquals(supplier.getId(), productService.findProductBySku("SKU-E2E-A").orElseThrow().getSupplierId());
        assertEquals(supplier.getId(), productService.findProductBySku("SKU-E2E-B").orElseThrow().getSupplierId());

        // Step 4: Create a purchase order with two product lines
        PurchaseOrder order = poService.createDraftOrder("PO-E2E-100", "SUP-E2E-1");
        PurchaseOrderItem itemA = poService.addOrUpdateItem("PO-E2E-100", "SKU-E2E-A", 20, new BigDecimal("85.00"));
        PurchaseOrderItem itemB = poService.addOrUpdateItem("PO-E2E-100", "SKU-E2E-B", 30, new BigDecimal("9.50"));
        assertEquals(PurchaseOrderStatus.DRAFT, order.getStatus());
        assertEquals(2, order.getItems().size());

        // Step 5: Place the purchase order
        PurchaseOrder placedOrder = poService.placeOrder("PO-E2E-100");
        assertEquals(PurchaseOrderStatus.PLACED, placedOrder.getStatus());
        assertEquals(10, productService.findProductBySku("SKU-E2E-A").orElseThrow().getQuantity());
        assertEquals(50, productService.findProductBySku("SKU-E2E-B").orElseThrow().getQuantity());

        // Step 6: Receive a partial delivery (10 drills out of 20, 15 goggles out of 30)
        PurchaseOrderReceipt partialReceipt = poService.receiveGoods("PO-E2E-100", List.of(
                new ReceiptLineInput(itemA.getId(), 10),
                new ReceiptLineInput(itemB.getId(), 15)
        ), "Partial delivery 1");
        assertNotNull(partialReceipt.getId());

        // Step 7: Verify product quantities and STOCK_IN history
        Product prodAAfterPartial = productService.findProductBySku("SKU-E2E-A").orElseThrow();
        Product prodBAfterPartial = productService.findProductBySku("SKU-E2E-B").orElseThrow();
        assertEquals(20, prodAAfterPartial.getQuantity()); // 10 initial + 10 received
        assertEquals(65, prodBAfterPartial.getQuantity()); // 50 initial + 15 received

        List<InventoryTransaction> txHistoryA = transactionRepository.findByProductId(prodA.getId());
        List<InventoryTransaction> txHistoryB = transactionRepository.findByProductId(prodB.getId());
        assertEquals(1, txHistoryA.size());
        assertEquals(TransactionType.STOCK_IN, txHistoryA.get(0).getType());
        assertEquals(10, txHistoryA.get(0).getQuantity());
        assertEquals(1, txHistoryB.size());
        assertEquals(TransactionType.STOCK_IN, txHistoryB.get(0).getType());
        assertEquals(15, txHistoryB.get(0).getQuantity());

        PurchaseOrder partiallyReceivedOrder = poService.getOrderByNumber("PO-E2E-100").orElseThrow();
        assertEquals(PurchaseOrderStatus.PARTIALLY_RECEIVED, partiallyReceivedOrder.getStatus());

        // Step 8: Receive the remaining goods (10 drills and 15 goggles)
        PurchaseOrderReceipt finalReceipt = poService.receiveGoods("PO-E2E-100", List.of(
                new ReceiptLineInput(itemA.getId(), 10),
                new ReceiptLineInput(itemB.getId(), 15)
        ), "Final delivery completion");
        assertNotNull(finalReceipt.getId());

        // Step 9: Verify order status becomes RECEIVED
        PurchaseOrder fullyReceivedOrder = poService.getOrderByNumber("PO-E2E-100").orElseThrow();
        assertEquals(PurchaseOrderStatus.RECEIVED, fullyReceivedOrder.getStatus());
        assertEquals(30, productService.findProductBySku("SKU-E2E-A").orElseThrow().getQuantity()); // 20 + 10 = 30
        assertEquals(80, productService.findProductBySku("SKU-E2E-B").orElseThrow().getQuantity()); // 65 + 15 = 80

        // Step 10: Return some damaged goods (2 drills from the final receipt line)
        long receiptLineA2 = finalReceipt.getItems().stream()
                .filter(i -> i.getProductId().equals(prodA.getId()))
                .findFirst().orElseThrow().getId();

        SupplierReturn supplierReturn = poService.createSupplierReturn("PO-E2E-100", List.of(
                new ReturnLineInput(receiptLineA2, 2, "Cracked outer casing")
        ), "Defective unit return");
        assertNotNull(supplierReturn.getId());

        // Step 11: Verify stock decreases and STOCK_OUT history is created
        assertEquals(28, productService.findProductBySku("SKU-E2E-A").orElseThrow().getQuantity()); // 30 - 2 = 28
        List<InventoryTransaction> txHistoryAfterReturn = transactionRepository.findByProductId(prodA.getId());
        assertEquals(3, txHistoryAfterReturn.size()); // 2 STOCK_IN + 1 STOCK_OUT
        InventoryTransaction stockOutTx = txHistoryAfterReturn.stream()
                .filter(t -> t.getType() == TransactionType.STOCK_OUT)
                .findFirst().orElseThrow();
        assertEquals(2, stockOutTx.getQuantity());

        // Step 12: Verify receipt and return histories
        List<PurchaseOrderReceipt> receipts = receiptRepository.findReceiptsByOrderId(placedOrder.getId());
        assertEquals(2, receipts.size());
        List<SupplierReturn> returns = poService.getReturnsForOrder("PO-E2E-100");
        assertEquals(1, returns.size());
        assertEquals(2, returns.get(0).getTotalReturnedUnits());

        // Step 13: Verify inventory and purchasing reports
        InventorySummary summary = productService.getInventorySummary();
        assertEquals(2, summary.getTotalProducts());
        assertEquals(108, summary.getTotalUnits()); // 28 + 80 = 108

        List<OutstandingPurchaseOrderReportItem> outstanding = poService.getOutstandingPurchaseOrdersReport();
        assertTrue(outstanding.isEmpty(), "Order is RECEIVED, so it should not appear in outstanding orders");

        List<ReceivingHistoryReportItem> recvReport = poService.getReceivingHistoryReport(
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
        assertEquals(4, recvReport.size()); // 2 items in receipt 1 + 2 items in receipt 2

        List<SupplierPurchasingSummaryItem> suppSummary = poService.getSupplierPurchasingSummary();
        assertEquals(1, suppSummary.size());
        assertEquals("SUP-E2E-1", suppSummary.get(0).getSupplierCode());
        assertEquals(1, suppSummary.get(0).getOrderCount());
        assertEquals(50, suppSummary.get(0).getTotalOrderedUnits());
        assertEquals(50, suppSummary.get(0).getTotalReceivedUnits());

        // Step 14: Verify historical prices remain unchanged
        // Update product selling prices
        productService.updateProduct("SKU-E2E-A", "Industrial Drill Pro", new BigDecimal("150.00"), 5);
        productService.updateProduct("SKU-E2E-B", "Safety Goggles Deluxe", new BigDecimal("25.00"), 10);

        // Historical PO line costs remain strictly untouched
        PurchaseOrder recheckedOrder = poService.getOrderByNumber("PO-E2E-100").orElseThrow();
        for (PurchaseOrderItem itm : recheckedOrder.getItems()) {
            if ("SKU-E2E-A".equals(itm.getProductSku())) {
                assertEquals(new BigDecimal("85.00"), itm.getUnitCost());
            } else if ("SKU-E2E-B".equals(itm.getProductSku())) {
                assertEquals(new BigDecimal("9.50"), itm.getUnitCost());
            }
        }
    }
}
