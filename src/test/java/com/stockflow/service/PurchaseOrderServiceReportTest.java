package com.stockflow.service;

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
import com.stockflow.repository.InMemoryInventoryTransactionRepository;
import com.stockflow.repository.InMemoryProductRepository;
import com.stockflow.repository.InMemoryPurchaseOrderReceiptRepository;
import com.stockflow.repository.InMemoryPurchaseOrderReportRepository;
import com.stockflow.repository.InMemoryPurchaseOrderRepository;
import com.stockflow.repository.InMemorySupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PurchaseOrderService Report Methods Tests")
class PurchaseOrderServiceReportTest {

    private InMemoryPurchaseOrderRepository poRepository;
    private InMemorySupplierRepository supplierRepository;
    private InMemoryProductRepository productRepository;
    private InMemoryInventoryTransactionRepository transactionRepository;
    private InMemoryPurchaseOrderReceiptRepository receiptRepository;
    private InMemoryPurchaseOrderReportRepository reportRepository;
    private PurchaseOrderService poService;

    @BeforeEach
    void setUp() {
        poRepository = new InMemoryPurchaseOrderRepository();
        supplierRepository = new InMemorySupplierRepository();
        productRepository = new InMemoryProductRepository();
        transactionRepository = new InMemoryInventoryTransactionRepository(productRepository);
        receiptRepository = new InMemoryPurchaseOrderReceiptRepository(poRepository, productRepository, transactionRepository);
        reportRepository = new InMemoryPurchaseOrderReportRepository(poRepository, supplierRepository, receiptRepository);
        poService = new PurchaseOrderService(poRepository, supplierRepository, productRepository, receiptRepository, reportRepository);
    }

    @Test
    @DisplayName("Should delegate outstanding purchase orders report to repository")
    void shouldDelegateOutstandingOrdersReport() {
        Supplier supplier = supplierRepository.save(new Supplier("SUP-10", "Tools Inc", null, null, null, null));
        Product product = productRepository.save(new Product("SKU-10", "Drill", new BigDecimal("50.00"), 20, 2));

        PurchaseOrder order = poService.createDraftOrder("PO-SERV-1", "SUP-10");
        poService.addOrUpdateItem("PO-SERV-1", "SKU-10", 5, new BigDecimal("45.00"));
        poService.placeOrder("PO-SERV-1");

        List<OutstandingPurchaseOrderReportItem> report = poService.getOutstandingPurchaseOrdersReport();
        assertEquals(1, report.size());
        assertEquals("PO-SERV-1", report.get(0).getOrderNumber());
        assertEquals("SUP-10", report.get(0).getSupplierCode());
        assertEquals(5, report.get(0).getOrderedUnits());
        assertEquals(5, report.get(0).getRemainingUnits());
    }

    @Test
    @DisplayName("Should validate date range and delegate receiving history report")
    void shouldValidateDateRangeAndDelegateReceivingHistory() {
        Supplier supplier = supplierRepository.save(new Supplier("SUP-11", "Parts Inc", null, null, null, null));
        Product product = productRepository.save(new Product("SKU-11", "Saw", new BigDecimal("30.00"), 10, 2));

        PurchaseOrder order = poService.createDraftOrder("PO-SERV-2", "SUP-11");
        poService.addOrUpdateItem("PO-SERV-2", "SKU-11", 10, new BigDecimal("25.00"));
        poService.placeOrder("PO-SERV-2");

        PurchaseOrder placed = poService.getOrderByNumber("PO-SERV-2").orElseThrow();
        Long itemId = placed.getItems().get(0).getId();
        poService.receiveGoods("PO-SERV-2", List.of(new ReceiptLineInput(itemId, 4)), "First 4 units");

        LocalDate today = LocalDate.now();

        // Valid query
        List<ReceivingHistoryReportItem> items = poService.getReceivingHistoryReport(today.minusDays(1), today.plusDays(1));
        assertEquals(1, items.size());
        assertEquals("PO-SERV-2", items.get(0).getOrderNumber());
        assertEquals(4, items.get(0).getReceivedQuantity());

        // Invalid query: start > end
        assertThrows(ValidationException.class, () ->
                poService.getReceivingHistoryReport(today.plusDays(1), today));

        // Invalid query: null start
        assertThrows(ValidationException.class, () ->
                poService.getReceivingHistoryReport(null, today));

        // Invalid query: null end
        assertThrows(ValidationException.class, () ->
                poService.getReceivingHistoryReport(today, null));
    }

    @Test
    @DisplayName("Should delegate supplier purchasing summary to repository")
    void shouldDelegateSupplierPurchasingSummary() {
        supplierRepository.save(new Supplier("SUP-12", "Hardware Pro", null, null, null, null));

        List<SupplierPurchasingSummaryItem> summary = poService.getSupplierPurchasingSummary();
        assertEquals(1, summary.size());
        assertEquals("SUP-12", summary.get(0).getSupplierCode());
        assertEquals(0, summary.get(0).getOrderCount());
    }

    @Test
    @DisplayName("Should throw IllegalStateException if reportRepository is not configured")
    void shouldThrowIfReportRepositoryMissing() {
        PurchaseOrderService unconfigured = new PurchaseOrderService(poRepository, supplierRepository, productRepository, receiptRepository);

        assertThrows(IllegalStateException.class, unconfigured::getOutstandingPurchaseOrdersReport);
        assertThrows(IllegalStateException.class, () -> unconfigured.getReceivingHistoryReport(LocalDate.now(), LocalDate.now()));
        assertThrows(IllegalStateException.class, unconfigured::getSupplierPurchasingSummary);
    }
}
