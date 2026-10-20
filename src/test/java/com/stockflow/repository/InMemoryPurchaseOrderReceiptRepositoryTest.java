package com.stockflow.repository;

import com.stockflow.exception.ValidationException;
import com.stockflow.model.InventoryTransaction;
import com.stockflow.model.Product;
import com.stockflow.model.PurchaseOrder;
import com.stockflow.model.PurchaseOrderItem;
import com.stockflow.model.PurchaseOrderReceipt;
import com.stockflow.model.PurchaseOrderStatus;
import com.stockflow.model.ReceiptLineInput;
import com.stockflow.model.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InMemoryPurchaseOrderReceiptRepository Tests")
class InMemoryPurchaseOrderReceiptRepositoryTest {

    private InMemoryProductRepository productRepository;
    private InMemoryInventoryTransactionRepository transactionRepository;
    private InMemoryPurchaseOrderRepository poRepository;
    private InMemoryPurchaseOrderReceiptRepository receiptRepository;

    private Product product1;
    private Product product2;
    private PurchaseOrder placedOrder;
    private PurchaseOrderItem item1;
    private PurchaseOrderItem item2;

    @BeforeEach
    void setUp() {
        productRepository = new InMemoryProductRepository();
        transactionRepository = new InMemoryInventoryTransactionRepository(productRepository);
        poRepository = new InMemoryPurchaseOrderRepository();
        receiptRepository = new InMemoryPurchaseOrderReceiptRepository(poRepository, productRepository, transactionRepository);

        product1 = productRepository.save(new Product("SKU-1", "Product 1", new BigDecimal("10.00"), 100, 5));
        product2 = productRepository.save(new Product("SKU-2", "Product 2", new BigDecimal("20.00"), 50, 5));

        placedOrder = poRepository.save(new PurchaseOrder("PO-RECV-1", 1L));
        item1 = poRepository.saveOrUpdateItem(placedOrder.getId(),
                new PurchaseOrderItem(placedOrder.getId(), product1.getId(), product1.getSku(), product1.getName(), 20, new BigDecimal("8.00")));
        item2 = poRepository.saveOrUpdateItem(placedOrder.getId(),
                new PurchaseOrderItem(placedOrder.getId(), product2.getId(), product2.getSku(), product2.getName(), 10, new BigDecimal("16.00")));

        poRepository.updateStatus(placedOrder.getId(), PurchaseOrderStatus.PLACED);
        placedOrder.setStatus(PurchaseOrderStatus.PLACED);
    }

    @Test
    @DisplayName("Should record valid full delivery and mark order RECEIVED")
    void shouldRecordFullDelivery() {
        List<ReceiptLineInput> lines = List.of(
                new ReceiptLineInput(item1.getId(), 20),
                new ReceiptLineInput(item2.getId(), 10)
        );

        PurchaseOrderReceipt receipt = receiptRepository.recordReceipt(placedOrder.getId(), "REC-FULL-01", "Full delivery", lines);

        assertNotNull(receipt.getId());
        assertEquals("REC-FULL-01", receipt.getReceiptNumber());
        assertEquals(2, receipt.getItems().size());
        assertEquals(30, receipt.getTotalReceivedQuantity());

        // Verify stock increases: 100 + 20 = 120, 50 + 10 = 60
        assertEquals(120, productRepository.findBySku("SKU-1").orElseThrow().getQuantity());
        assertEquals(60, productRepository.findBySku("SKU-2").orElseThrow().getQuantity());

        // Verify transactions created
        List<InventoryTransaction> txs1 = transactionRepository.findByProductId(product1.getId());
        assertEquals(1, txs1.size());
        assertEquals(TransactionType.STOCK_IN, txs1.get(0).getType());
        assertEquals(20, txs1.get(0).getQuantity());

        // Verify order status updated to RECEIVED
        assertEquals(PurchaseOrderStatus.RECEIVED, placedOrder.getStatus());
        assertEquals(PurchaseOrderStatus.RECEIVED, poRepository.findById(placedOrder.getId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("Should record partial delivery and mark order PARTIALLY_RECEIVED")
    void shouldRecordPartialDelivery() {
        List<ReceiptLineInput> lines = List.of(
                new ReceiptLineInput(item1.getId(), 8)
        );

        PurchaseOrderReceipt receipt = receiptRepository.recordReceipt(placedOrder.getId(), "REC-PART-01", "Partial 1", lines);

        assertEquals(8, receipt.getTotalReceivedQuantity());
        assertEquals(108, productRepository.findBySku("SKU-1").orElseThrow().getQuantity());
        assertEquals(50, productRepository.findBySku("SKU-2").orElseThrow().getQuantity());

        assertEquals(PurchaseOrderStatus.PARTIALLY_RECEIVED, placedOrder.getStatus());
        assertEquals(8, item1.getReceivedQuantity());
        assertEquals(12, item1.getRemainingQuantity());
    }

    @Test
    @DisplayName("Should record multiple deliveries across time until order is fully RECEIVED")
    void shouldRecordMultipleDeliveriesUntilFullyReceived() {
        // Delivery 1: 5 of item 1
        receiptRepository.recordReceipt(placedOrder.getId(), "REC-1", "D1", List.of(new ReceiptLineInput(item1.getId(), 5)));
        assertEquals(PurchaseOrderStatus.PARTIALLY_RECEIVED, placedOrder.getStatus());
        assertEquals(15, item1.getRemainingQuantity());

        // Delivery 2: 15 of item 1, 4 of item 2
        receiptRepository.recordReceipt(placedOrder.getId(), "REC-2", "D2", List.of(
                new ReceiptLineInput(item1.getId(), 15),
                new ReceiptLineInput(item2.getId(), 4)
        ));
        assertEquals(PurchaseOrderStatus.PARTIALLY_RECEIVED, placedOrder.getStatus());
        assertEquals(0, item1.getRemainingQuantity());
        assertTrue(item1.isFullyReceived());
        assertEquals(6, item2.getRemainingQuantity());

        // Delivery 3: remaining 6 of item 2
        receiptRepository.recordReceipt(placedOrder.getId(), "REC-3", "D3", List.of(new ReceiptLineInput(item2.getId(), 6)));
        assertEquals(PurchaseOrderStatus.RECEIVED, placedOrder.getStatus());
        assertTrue(placedOrder.isFullyReceived());

        // Verify total receipts count
        List<PurchaseOrderReceipt> receipts = receiptRepository.findReceiptsByOrderId(placedOrder.getId());
        assertEquals(3, receipts.size());
    }

    @Test
    @DisplayName("Should reject receiving more than remaining quantity")
    void shouldRejectReceivingMoreThanRemaining() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                receiptRepository.recordReceipt(placedOrder.getId(), "REC-OVER", null,
                        List.of(new ReceiptLineInput(item1.getId(), 21))));
        assertTrue(ex.getMessage().contains("exceeds remaining quantity"));
    }

    @Test
    @DisplayName("Should reject receiving against DRAFT or CANCELLED orders")
    void shouldRejectReceivingAgainstDraftOrCancelled() {
        PurchaseOrder draftOrder = poRepository.save(new PurchaseOrder("PO-DRAFT", 1L));
        PurchaseOrderItem draftItem = poRepository.saveOrUpdateItem(draftOrder.getId(),
                new PurchaseOrderItem(draftOrder.getId(), product1.getId(), product1.getSku(), product1.getName(), 10, new BigDecimal("5.00")));

        assertThrows(ValidationException.class, () ->
                receiptRepository.recordReceipt(draftOrder.getId(), "REC-ERR", null,
                        List.of(new ReceiptLineInput(draftItem.getId(), 5))));

        poRepository.updateStatus(draftOrder.getId(), PurchaseOrderStatus.CANCELLED);
        draftOrder.setStatus(PurchaseOrderStatus.CANCELLED);

        assertThrows(ValidationException.class, () ->
                receiptRepository.recordReceipt(draftOrder.getId(), "REC-ERR2", null,
                        List.of(new ReceiptLineInput(draftItem.getId(), 5))));
    }

    @Test
    @DisplayName("Should reject receiving against already fully RECEIVED order")
    void shouldRejectReceivingAgainstFullyReceivedOrder() {
        receiptRepository.recordReceipt(placedOrder.getId(), "REC-FULL", null, List.of(
                new ReceiptLineInput(item1.getId(), 20),
                new ReceiptLineInput(item2.getId(), 10)
        ));
        assertEquals(PurchaseOrderStatus.RECEIVED, placedOrder.getStatus());

        assertThrows(ValidationException.class, () ->
                receiptRepository.recordReceipt(placedOrder.getId(), "REC-AGAIN", null,
                        List.of(new ReceiptLineInput(item1.getId(), 1))));
    }
}
