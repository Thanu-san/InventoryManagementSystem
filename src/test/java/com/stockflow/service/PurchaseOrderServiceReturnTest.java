package com.stockflow.service;

import com.stockflow.exception.ValidationException;
import com.stockflow.model.Product;
import com.stockflow.model.PurchaseOrder;
import com.stockflow.model.PurchaseOrderItem;
import com.stockflow.model.ReceiptLineInput;
import com.stockflow.model.ReceiptLineReturnableInfo;
import com.stockflow.model.ReturnLineInput;
import com.stockflow.model.Supplier;
import com.stockflow.model.SupplierReturn;
import com.stockflow.model.SupplierReturnItem;
import com.stockflow.repository.InMemoryInventoryTransactionRepository;
import com.stockflow.repository.InMemoryProductRepository;
import com.stockflow.repository.InMemoryPurchaseOrderReceiptRepository;
import com.stockflow.repository.InMemoryPurchaseOrderReportRepository;
import com.stockflow.repository.InMemoryPurchaseOrderRepository;
import com.stockflow.repository.InMemorySupplierRepository;
import com.stockflow.repository.InMemorySupplierReturnRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PurchaseOrderService Supplier Returns Tests")
class PurchaseOrderServiceReturnTest {

    private InMemoryPurchaseOrderRepository poRepository;
    private InMemorySupplierRepository supplierRepository;
    private InMemoryProductRepository productRepository;
    private InMemoryInventoryTransactionRepository transactionRepository;
    private InMemoryPurchaseOrderReceiptRepository receiptRepository;
    private InMemoryPurchaseOrderReportRepository reportRepository;
    private InMemorySupplierReturnRepository returnRepository;
    private PurchaseOrderService poService;

    private Supplier testSupplier;
    private Product testProduct1;
    private Product testProduct2;
    private PurchaseOrder testOrder;
    private long poItem1Id;
    private long poItem2Id;

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

        testSupplier = supplierRepository.save(new Supplier("SUP-RET-1", "Return Supplier Inc", null, null, null, null));
        testProduct1 = productRepository.save(new Product("SKU-RET-1", "Product A", new BigDecimal("100.00"), 50, 5));
        testProduct2 = productRepository.save(new Product("SKU-RET-2", "Product B", new BigDecimal("50.00"), 20, 5));

        testOrder = poService.createDraftOrder("PO-RET-001", "SUP-RET-1");
        poService.addOrUpdateItem("PO-RET-001", "SKU-RET-1", 10, new BigDecimal("80.00"));
        poService.addOrUpdateItem("PO-RET-001", "SKU-RET-2", 5, new BigDecimal("40.00"));
        poService.placeOrder("PO-RET-001");

        PurchaseOrder currentOrder = poService.getOrderByNumber("PO-RET-001").orElseThrow();
        for (PurchaseOrderItem item : currentOrder.getItems()) {
            if ("SKU-RET-1".equals(item.getProductSku())) {
                poItem1Id = item.getId();
            } else if ("SKU-RET-2".equals(item.getProductSku())) {
                poItem2Id = item.getId();
            }
        }
    }

    @Test
    @DisplayName("Should create supplier return successfully and reduce on-hand stock")
    void shouldCreateSupplierReturnSuccessfully() {
        var receipt = poService.receiveGoods("PO-RET-001", List.of(
                new ReceiptLineInput(poItem1Id, 10),
                new ReceiptLineInput(poItem2Id, 5)
        ), "Initial delivery");

        long receiptItemId = receipt.getItems().get(0).getId();

        SupplierReturn ret = poService.createSupplierReturn("PO-RET-001", List.of(
                new ReturnLineInput(receiptItemId, 3, "Damaged in transit")
        ), "Defective shipment return");

        assertNotNull(ret);
        assertNotNull(ret.getId());
        assertTrue(ret.getReturnNumber().startsWith("RET-"));
        assertEquals(testOrder.getId(), ret.getPurchaseOrderId());
        assertEquals("Defective shipment return", ret.getReason());
        assertEquals(1, ret.getItems().size());

        SupplierReturnItem item = ret.getItems().get(0);
        assertEquals(receiptItemId, item.getPurchaseOrderReceiptItemId());
        assertEquals(testProduct1.getId(), item.getProductId());
        assertEquals(3, item.getQuantity());
        assertEquals("Damaged in transit", item.getReason());
        assertNotNull(item.getInventoryTransactionId());

        // Initial product stock was 50 + 10 received = 60; returned 3 -> 57
        Product updatedProduct = productRepository.findBySku(testProduct1.getSku()).orElseThrow();
        assertEquals(57, updatedProduct.getQuantity());
    }

    @Test
    @DisplayName("Should reject return if order is DRAFT or CANCELLED")
    void shouldRejectReturnIfOrderNotPlacedOrReceived() {
        poService.createDraftOrder("PO-DRAFT-001", "SUP-RET-1");
        assertThrows(ValidationException.class, () ->
                poService.createSupplierReturn("PO-DRAFT-001", List.of(new ReturnLineInput(1L, 1, "Reason")), "Notes")
        );

        poService.createDraftOrder("PO-CANCEL-001", "SUP-RET-1");
        poService.cancelOrder("PO-CANCEL-001");
        assertThrows(ValidationException.class, () ->
                poService.createSupplierReturn("PO-CANCEL-001", List.of(new ReturnLineInput(1L, 1, "Reason")), "Notes")
        );
    }

    @Test
    @DisplayName("Should validate null or empty parameters in createSupplierReturn")
    void shouldValidateNullOrEmptyParameters() {
        assertThrows(ValidationException.class, () ->
                poService.createSupplierReturn("", List.of(new ReturnLineInput(1L, 1, "Reason")), "Notes")
        );

        assertThrows(ValidationException.class, () ->
                poService.createSupplierReturn("PO-RET-001", null, "Notes")
        );

        assertThrows(ValidationException.class, () ->
                poService.createSupplierReturn("PO-RET-001", Collections.emptyList(), "Notes")
        );
    }

    @Test
    @DisplayName("Should throw IllegalStateException when return repository is not configured")
    void shouldThrowWhenReturnRepositoryNotConfigured() {
        PurchaseOrderService limitedService = new PurchaseOrderService(poRepository, supplierRepository, productRepository);
        assertThrows(IllegalStateException.class, () ->
                limitedService.createSupplierReturn("PO-RET-001", List.of(new ReturnLineInput(1L, 1, "Reason")), "Notes")
        );
        assertTrue(limitedService.getReturnableReceiptLinesForOrder("PO-RET-001").isEmpty());
        assertTrue(limitedService.getReturnsForOrder("PO-RET-001").isEmpty());
        assertTrue(limitedService.getReturnByNumber("RET-001").isEmpty());
    }

    @Test
    @DisplayName("Should return accurate returnable receipt lines for order")
    void shouldGetReturnableReceiptLinesForOrder() {
        var receipt = poService.receiveGoods("PO-RET-001", List.of(
                new ReceiptLineInput(poItem1Id, 8)
        ), "Partial receive");

        List<ReceiptLineReturnableInfo> lines = poService.getReturnableReceiptLinesForOrder("PO-RET-001");
        assertEquals(1, lines.size());
        ReceiptLineReturnableInfo line = lines.get(0);
        assertEquals(receipt.getItems().get(0).getId(), line.getReceiptItemId());
        assertEquals(8, line.getReceivedQuantity());
        assertEquals(0, line.getPreviouslyReturnedQuantity());
        assertEquals(8, line.getReturnableQuantity());

        // Perform partial return of 3
        poService.createSupplierReturn("PO-RET-001", List.of(
                new ReturnLineInput(line.getReceiptItemId(), 3, "Wrong color")
        ), "First return");

        List<ReceiptLineReturnableInfo> linesAfter = poService.getReturnableReceiptLinesForOrder("PO-RET-001");
        assertEquals(1, linesAfter.size());
        assertEquals(3, linesAfter.get(0).getPreviouslyReturnedQuantity());
        assertEquals(5, linesAfter.get(0).getReturnableQuantity());
    }

    @Test
    @DisplayName("Should retrieve returns for order and by return number")
    void shouldRetrieveReturnsForOrderAndByNumber() {
        var receipt = poService.receiveGoods("PO-RET-001", List.of(
                new ReceiptLineInput(poItem1Id, 6)
        ), "Receive");

        SupplierReturn ret1 = poService.createSupplierReturn("PO-RET-001", List.of(
                new ReturnLineInput(receipt.getItems().get(0).getId(), 2, "Defect 1")
        ), "Return 1");

        SupplierReturn ret2 = poService.createSupplierReturn("PO-RET-001", List.of(
                new ReturnLineInput(receipt.getItems().get(0).getId(), 2, "Defect 2")
        ), "Return 2");

        List<SupplierReturn> orderReturns = poService.getReturnsForOrder("PO-RET-001");
        assertEquals(2, orderReturns.size());

        Optional<SupplierReturn> foundOpt = poService.getReturnByNumber(ret1.getReturnNumber());
        assertTrue(foundOpt.isPresent());
        assertEquals(ret1.getReturnNumber(), foundOpt.get().getReturnNumber());

        Optional<SupplierReturn> notFound = poService.getReturnByNumber("NON-EXISTENT");
        assertTrue(notFound.isEmpty());
    }

    @Test
    @DisplayName("Should reject return when return quantity exceeds available product stock")
    void shouldRejectWhenQuantityExceedsAvailableStock() {
        var receipt = poService.receiveGoods("PO-RET-001", List.of(
                new ReceiptLineInput(poItem2Id, 5)
        ), "Delivery");

        // Force product quantity to 2 (e.g. sold or transferred out)
        productRepository.updateQuantity(testProduct2.getId(), 2);

        // Attempting to return 4 (less than 5 received, but more than 2 in stock)
        ValidationException ex = assertThrows(ValidationException.class, () ->
                poService.createSupplierReturn("PO-RET-001", List.of(
                        new ReturnLineInput(receipt.getItems().get(0).getId(), 4, "Damaged")
                ), "Attempt exceeding stock")
        );
        assertTrue(ex.getMessage().contains("Insufficient stock"));
    }

    @Test
    @DisplayName("Should reject return when return quantity exceeds returnable quantity")
    void shouldRejectWhenQuantityExceedsReturnable() {
        var receipt = poService.receiveGoods("PO-RET-001", List.of(
                new ReceiptLineInput(poItem2Id, 5)
        ), "Delivery");

        ValidationException ex = assertThrows(ValidationException.class, () ->
                poService.createSupplierReturn("PO-RET-001", List.of(
                        new ReturnLineInput(receipt.getItems().get(0).getId(), 6, "Exceeds received")
                ), "Excessive return")
        );
        assertTrue(ex.getMessage().contains("exceeds returnable quantity"));
    }
}
