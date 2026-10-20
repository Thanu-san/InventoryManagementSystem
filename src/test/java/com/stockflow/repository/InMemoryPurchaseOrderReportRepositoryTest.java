package com.stockflow.repository;

import com.stockflow.exception.ValidationException;
import com.stockflow.model.OutstandingPurchaseOrderReportItem;
import com.stockflow.model.Product;
import com.stockflow.model.PurchaseOrder;
import com.stockflow.model.PurchaseOrderItem;
import com.stockflow.model.PurchaseOrderStatus;
import com.stockflow.model.ReceiptLineInput;
import com.stockflow.model.ReceivingHistoryReportItem;
import com.stockflow.model.Supplier;
import com.stockflow.model.SupplierPurchasingSummaryItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InMemoryPurchaseOrderReportRepository Unit Tests")
class InMemoryPurchaseOrderReportRepositoryTest {

    private InMemoryPurchaseOrderRepository poRepository;
    private InMemorySupplierRepository supplierRepository;
    private InMemoryProductRepository productRepository;
    private InMemoryInventoryTransactionRepository transactionRepository;
    private InMemoryPurchaseOrderReceiptRepository receiptRepository;
    private InMemoryPurchaseOrderReportRepository reportRepository;

    private Supplier supplierA;
    private Supplier supplierB;
    private Supplier supplierC; // Zero orders
    private Product product1;
    private Product product2;

    @BeforeEach
    void setUp() {
        poRepository = new InMemoryPurchaseOrderRepository();
        supplierRepository = new InMemorySupplierRepository();
        productRepository = new InMemoryProductRepository();
        transactionRepository = new InMemoryInventoryTransactionRepository(productRepository);
        receiptRepository = new InMemoryPurchaseOrderReceiptRepository(poRepository, productRepository, transactionRepository);
        reportRepository = new InMemoryPurchaseOrderReportRepository(poRepository, supplierRepository, receiptRepository);

        supplierA = supplierRepository.save(new Supplier("SUP-A", "Alpha Supplies", null, null, null, null));
        supplierB = supplierRepository.save(new Supplier("SUP-B", "Beta Industrial", null, null, null, null));
        supplierC = supplierRepository.save(new Supplier("SUP-C", "Gamma Logistics", null, null, null, null));

        product1 = productRepository.save(new Product("SKU-1", "Hammer", new BigDecimal("25.00"), 100, 10));
        product2 = productRepository.save(new Product("SKU-2", "Wrench", new BigDecimal("15.00"), 50, 5));
    }

    @Test
    @DisplayName("Should return empty list for outstanding orders when no orders exist")
    void shouldReturnEmptyWhenNoOrdersExist() {
        List<OutstandingPurchaseOrderReportItem> report = reportRepository.getOutstandingPurchaseOrdersReport();
        assertNotNull(report);
        assertTrue(report.isEmpty());
    }

    @Test
    @DisplayName("Should only include PLACED and PARTIALLY_RECEIVED orders in outstanding report")
    void shouldFilterStatusesCorrectlyInOutstandingReport() {
        // 1. DRAFT order
        PurchaseOrder draft = new PurchaseOrder(null, "PO-DRAFT", supplierA.getId(), PurchaseOrderStatus.DRAFT, LocalDateTime.now().minusDays(5), null);
        draft.addOrUpdateItem(new PurchaseOrderItem(product1.getId(), product1.getSku(), product1.getName(), 10, new BigDecimal("20.00")));
        poRepository.save(draft);

        // 2. PLACED order
        PurchaseOrder placed = new PurchaseOrder(null, "PO-PLACED", supplierA.getId(), PurchaseOrderStatus.PLACED, LocalDateTime.now().minusDays(4), null);
        placed.addOrUpdateItem(new PurchaseOrderItem(product1.getId(), product1.getSku(), product1.getName(), 20, new BigDecimal("20.00")));
        poRepository.save(placed);

        // 3. PARTIALLY_RECEIVED order
        PurchaseOrder partial = new PurchaseOrder(null, "PO-PARTIAL", supplierB.getId(), PurchaseOrderStatus.PLACED, LocalDateTime.now().minusDays(3), null);
        PurchaseOrderItem partialItem = new PurchaseOrderItem(product2.getId(), product2.getSku(), product2.getName(), 30, new BigDecimal("12.00"));
        partial.addOrUpdateItem(partialItem);
        poRepository.save(partial);
        // Receive partial
        receiptRepository.recordReceipt(partial.getId(), "REC-PARTIAL", "Delivery 1", List.of(new ReceiptLineInput(partial.getItems().get(0).getId(), 10)));

        // 4. RECEIVED order
        PurchaseOrder received = new PurchaseOrder(null, "PO-RECEIVED", supplierA.getId(), PurchaseOrderStatus.PLACED, LocalDateTime.now().minusDays(2), null);
        PurchaseOrderItem recItem = new PurchaseOrderItem(product1.getId(), product1.getSku(), product1.getName(), 15, new BigDecimal("20.00"));
        received.addOrUpdateItem(recItem);
        poRepository.save(received);
        receiptRepository.recordReceipt(received.getId(), "REC-FULL", "Delivery Full", List.of(new ReceiptLineInput(received.getItems().get(0).getId(), 15)));

        // 5. CANCELLED order
        PurchaseOrder cancelled = new PurchaseOrder(null, "PO-CANCEL", supplierB.getId(), PurchaseOrderStatus.CANCELLED, LocalDateTime.now().minusDays(1), null);
        cancelled.addOrUpdateItem(new PurchaseOrderItem(product2.getId(), product2.getSku(), product2.getName(), 5, new BigDecimal("12.00")));
        poRepository.save(cancelled);

        List<OutstandingPurchaseOrderReportItem> report = reportRepository.getOutstandingPurchaseOrdersReport();

        assertEquals(2, report.size());
        assertEquals("PO-PLACED", report.get(0).getOrderNumber());
        assertEquals(PurchaseOrderStatus.PLACED, report.get(0).getStatus());
        assertEquals(20, report.get(0).getOrderedUnits());
        assertEquals(0, report.get(0).getReceivedUnits());
        assertEquals(20, report.get(0).getRemainingUnits());
        assertEquals(1, report.get(0).getDistinctLineCount());

        assertEquals("PO-PARTIAL", report.get(1).getOrderNumber());
        assertEquals(PurchaseOrderStatus.PARTIALLY_RECEIVED, report.get(1).getStatus());
        assertEquals(30, report.get(1).getOrderedUnits());
        assertEquals(10, report.get(1).getReceivedUnits());
        assertEquals(20, report.get(1).getRemainingUnits());
    }

    @Test
    @DisplayName("Should sort outstanding orders by creation date ascending, then order number")
    void shouldSortOutstandingOrdersChronologically() {
        LocalDateTime t1 = LocalDateTime.of(2026, 1, 10, 8, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 1, 15, 8, 0);

        PurchaseOrder po2 = new PurchaseOrder(null, "PO-200", supplierA.getId(), PurchaseOrderStatus.PLACED, t2, null);
        po2.addOrUpdateItem(new PurchaseOrderItem(product1.getId(), product1.getSku(), product1.getName(), 10, new BigDecimal("20.00")));
        poRepository.save(po2);

        PurchaseOrder po1 = new PurchaseOrder(null, "PO-100", supplierA.getId(), PurchaseOrderStatus.PLACED, t1, null);
        po1.addOrUpdateItem(new PurchaseOrderItem(product1.getId(), product1.getSku(), product1.getName(), 10, new BigDecimal("20.00")));
        poRepository.save(po1);

        List<OutstandingPurchaseOrderReportItem> report = reportRepository.getOutstandingPurchaseOrdersReport();
        assertEquals(2, report.size());
        assertEquals("PO-100", report.get(0).getOrderNumber());
        assertEquals("PO-200", report.get(1).getOrderNumber());
    }

    @Test
    @DisplayName("Should correctly handle receiving history within date range")
    void shouldReturnReceivingHistoryWithinDateRange() {
        PurchaseOrder order = new PurchaseOrder(null, "PO-HIST", supplierA.getId(), PurchaseOrderStatus.PLACED, LocalDateTime.now().minusDays(10), null);
        PurchaseOrderItem item1 = new PurchaseOrderItem(product1.getId(), product1.getSku(), product1.getName(), 20, new BigDecimal("18.00"));
        order.addOrUpdateItem(item1);
        poRepository.save(order);

        receiptRepository.recordReceipt(order.getId(), "REC-1", "Batch 1", List.of(new ReceiptLineInput(order.getItems().get(0).getId(), 5)));
        receiptRepository.recordReceipt(order.getId(), "REC-2", "Batch 2", List.of(new ReceiptLineInput(order.getItems().get(0).getId(), 10)));

        LocalDate today = LocalDate.now();
        List<ReceivingHistoryReportItem> history = reportRepository.getReceivingHistoryReport(today.minusDays(1), today.plusDays(1));

        assertEquals(2, history.size());
        assertEquals("REC-1", history.get(0).getReceiptNumber());
        assertEquals("PO-HIST", history.get(0).getOrderNumber());
        assertEquals("Alpha Supplies", history.get(0).getSupplierName());
        assertEquals("SKU-1", history.get(0).getProductSku());
        assertEquals(5, history.get(0).getReceivedQuantity());
        assertEquals(new BigDecimal("18.00"), history.get(0).getAgreedUnitCost());
        assertEquals(new BigDecimal("90.00"), history.get(0).getLineTotalCost());

        assertEquals("REC-2", history.get(1).getReceiptNumber());
        assertEquals(10, history.get(1).getReceivedQuantity());
        assertEquals(new BigDecimal("180.00"), history.get(1).getLineTotalCost());
    }

    @Test
    @DisplayName("Should validate date range parameters for receiving history")
    void shouldValidateDateRangeParameters() {
        LocalDate today = LocalDate.now();

        assertThrows(ValidationException.class, () ->
                reportRepository.getReceivingHistoryReport(null, today));
        assertThrows(ValidationException.class, () ->
                reportRepository.getReceivingHistoryReport(today, null));
        assertThrows(ValidationException.class, () ->
                reportRepository.getReceivingHistoryReport(today.plusDays(1), today));
    }

    @Test
    @DisplayName("Should use historical agreed unit cost rather than product current price")
    void shouldUseHistoricalCostIndependentOfProductPrice() {
        PurchaseOrder order = new PurchaseOrder(null, "PO-HIST-COST", supplierA.getId(), PurchaseOrderStatus.PLACED, LocalDateTime.now(), null);
        PurchaseOrderItem item = new PurchaseOrderItem(product1.getId(), product1.getSku(), product1.getName(), 10, new BigDecimal("12.50"));
        order.addOrUpdateItem(item);
        poRepository.save(order);

        receiptRepository.recordReceipt(order.getId(), "REC-HIST-COST", "Note", List.of(new ReceiptLineInput(order.getItems().get(0).getId(), 4)));

        // Change product selling price in catalog
        product1.setPrice(new BigDecimal("99.99"));
        productRepository.update(product1);

        List<ReceivingHistoryReportItem> history = reportRepository.getReceivingHistoryReport(LocalDate.now(), LocalDate.now());
        assertEquals(1, history.size());
        assertEquals(new BigDecimal("12.50"), history.get(0).getAgreedUnitCost());
        assertEquals(new BigDecimal("50.00"), history.get(0).getLineTotalCost());
    }

    @Test
    @DisplayName("Should generate supplier purchasing summary including suppliers with zero orders")
    void shouldGenerateSupplierPurchasingSummary() {
        // Supplier A: 1 PLACED order (10 units @ $20 = $200), received 4 units ($80)
        PurchaseOrder poA = new PurchaseOrder(null, "PO-SUMM-A", supplierA.getId(), PurchaseOrderStatus.PLACED, LocalDateTime.now(), null);
        poA.addOrUpdateItem(new PurchaseOrderItem(product1.getId(), product1.getSku(), product1.getName(), 10, new BigDecimal("20.00")));
        poRepository.save(poA);
        receiptRepository.recordReceipt(poA.getId(), "REC-A", "Delivery", List.of(new ReceiptLineInput(poA.getItems().get(0).getId(), 4)));

        // Supplier B: 1 CANCELLED order (should be excluded) and 1 DRAFT order (should be excluded)
        PurchaseOrder poB1 = new PurchaseOrder(null, "PO-SUMM-B1", supplierB.getId(), PurchaseOrderStatus.CANCELLED, LocalDateTime.now(), null);
        poB1.addOrUpdateItem(new PurchaseOrderItem(product2.getId(), product2.getSku(), product2.getName(), 100, new BigDecimal("10.00")));
        poRepository.save(poB1);

        PurchaseOrder poB2 = new PurchaseOrder(null, "PO-SUMM-B2", supplierB.getId(), PurchaseOrderStatus.DRAFT, LocalDateTime.now(), null);
        poB2.addOrUpdateItem(new PurchaseOrderItem(product2.getId(), product2.getSku(), product2.getName(), 50, new BigDecimal("10.00")));
        poRepository.save(poB2);

        // Supplier C: 0 orders

        List<SupplierPurchasingSummaryItem> summary = reportRepository.getSupplierPurchasingSummary();

        assertEquals(3, summary.size());

        // Supplier A
        SupplierPurchasingSummaryItem sA = summary.get(0);
        assertEquals("SUP-A", sA.getSupplierCode());
        assertEquals(1, sA.getOrderCount());
        assertEquals(10, sA.getTotalOrderedUnits());
        assertEquals(4, sA.getTotalReceivedUnits());
        assertEquals(6, sA.getTotalOutstandingUnits());
        assertEquals(new BigDecimal("200.00"), sA.getTotalOrderValue());
        assertEquals(new BigDecimal("80.00"), sA.getTotalReceivedValue());

        // Supplier B (cancelled and draft excluded)
        SupplierPurchasingSummaryItem sB = summary.get(1);
        assertEquals("SUP-B", sB.getSupplierCode());
        assertEquals(0, sB.getOrderCount());
        assertEquals(0, sB.getTotalOrderedUnits());
        assertEquals(0, sB.getTotalReceivedUnits());
        assertEquals(0, sB.getTotalOutstandingUnits());
        assertEquals(BigDecimal.ZERO, sB.getTotalOrderValue());
        assertEquals(BigDecimal.ZERO, sB.getTotalReceivedValue());

        // Supplier C (no orders)
        SupplierPurchasingSummaryItem sC = summary.get(2);
        assertEquals("SUP-C", sC.getSupplierCode());
        assertEquals(0, sC.getOrderCount());
        assertEquals(0, sC.getTotalOrderedUnits());
        assertEquals(0, sC.getTotalReceivedUnits());
        assertEquals(0, sC.getTotalOutstandingUnits());
        assertEquals(BigDecimal.ZERO, sC.getTotalOrderValue());
        assertEquals(BigDecimal.ZERO, sC.getTotalReceivedValue());
    }

    @Test
    @DisplayName("Should prevent multiplication when multiple receipts occur on single line")
    void shouldNotMultiplyOrderedUnitsOrValuesWithMultipleReceipts() {
        PurchaseOrder po = new PurchaseOrder(null, "PO-MULTI-REC", supplierA.getId(), PurchaseOrderStatus.PLACED, LocalDateTime.now(), null);
        po.addOrUpdateItem(new PurchaseOrderItem(product1.getId(), product1.getSku(), product1.getName(), 10, new BigDecimal("20.00")));
        poRepository.save(po);

        // 3 separate deliveries against the same line
        receiptRepository.recordReceipt(po.getId(), "REC-M1", "Del 1", List.of(new ReceiptLineInput(po.getItems().get(0).getId(), 2)));
        receiptRepository.recordReceipt(po.getId(), "REC-M2", "Del 2", List.of(new ReceiptLineInput(po.getItems().get(0).getId(), 3)));
        receiptRepository.recordReceipt(po.getId(), "REC-M3", "Del 3", List.of(new ReceiptLineInput(po.getItems().get(0).getId(), 4)));

        // Outstanding report check
        List<OutstandingPurchaseOrderReportItem> outstanding = reportRepository.getOutstandingPurchaseOrdersReport();
        assertEquals(1, outstanding.size());
        assertEquals(10, outstanding.get(0).getOrderedUnits());
        assertEquals(9, outstanding.get(0).getReceivedUnits());
        assertEquals(1, outstanding.get(0).getRemainingUnits());

        // Supplier summary check
        List<SupplierPurchasingSummaryItem> summary = reportRepository.getSupplierPurchasingSummary();
        SupplierPurchasingSummaryItem supA = summary.stream()
                .filter(s -> "SUP-A".equals(s.getSupplierCode()))
                .findFirst().orElseThrow();

        assertEquals(1, supA.getOrderCount());
        assertEquals(10, supA.getTotalOrderedUnits()); // Must still be 10, not 30!
        assertEquals(9, supA.getTotalReceivedUnits());
        assertEquals(1, supA.getTotalOutstandingUnits());
        assertEquals(new BigDecimal("200.00"), supA.getTotalOrderValue()); // Must still be $200.00, not $600.00!
        assertEquals(new BigDecimal("180.00"), supA.getTotalReceivedValue());
    }
}
