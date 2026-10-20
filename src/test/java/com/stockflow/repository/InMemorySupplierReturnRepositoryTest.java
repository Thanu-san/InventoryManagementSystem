package com.stockflow.repository;

import com.stockflow.exception.ValidationException;
import com.stockflow.model.InventoryTransaction;
import com.stockflow.model.Product;
import com.stockflow.model.PurchaseOrder;
import com.stockflow.model.PurchaseOrderItem;
import com.stockflow.model.PurchaseOrderReceipt;
import com.stockflow.model.PurchaseOrderReceiptItem;
import com.stockflow.model.PurchaseOrderStatus;
import com.stockflow.model.ReceiptLineInput;
import com.stockflow.model.ReturnLineInput;
import com.stockflow.model.Supplier;
import com.stockflow.model.SupplierReturn;
import com.stockflow.model.SupplierReturnItem;
import com.stockflow.model.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InMemorySupplierReturnRepository Unit Tests")
class InMemorySupplierReturnRepositoryTest {

    private InMemoryPurchaseOrderRepository poRepository;
    private InMemorySupplierRepository supplierRepository;
    private InMemoryProductRepository productRepository;
    private InMemoryInventoryTransactionRepository transactionRepository;
    private InMemoryPurchaseOrderReceiptRepository receiptRepository;
    private InMemorySupplierReturnRepository returnRepository;

    private Supplier supplier;
    private Product product1;
    private Product product2;
    private PurchaseOrder order;
    private PurchaseOrderReceipt receipt;
    private PurchaseOrderReceiptItem receiptItem1;
    private PurchaseOrderReceiptItem receiptItem2;

    @BeforeEach
    void setUp() {
        poRepository = new InMemoryPurchaseOrderRepository();
        supplierRepository = new InMemorySupplierRepository();
        productRepository = new InMemoryProductRepository();
        transactionRepository = new InMemoryInventoryTransactionRepository(productRepository);
        receiptRepository = new InMemoryPurchaseOrderReceiptRepository(poRepository, productRepository, transactionRepository);
        returnRepository = new InMemorySupplierReturnRepository(poRepository, receiptRepository, productRepository, transactionRepository);

        supplier = supplierRepository.save(new Supplier("SUP-RET", "Return Supplier", null, null, null, null));
        // Product 1 initial stock: 20
        product1 = productRepository.save(new Product("SKU-RET-1", "Hammer", new BigDecimal("25.00"), 20, 5));
        // Product 2 initial stock: 30
        product2 = productRepository.save(new Product("SKU-RET-2", "Nails", new BigDecimal("5.00"), 30, 10));

        // Create PO
        order = new PurchaseOrder("PO-RET-01", supplier.getId());
        order.addOrUpdateItem(new PurchaseOrderItem(product1.getId(), product1.getSku(), product1.getName(), 10, new BigDecimal("20.00")));
        order.addOrUpdateItem(new PurchaseOrderItem(product2.getId(), product2.getSku(), product2.getName(), 15, new BigDecimal("4.00")));
        poRepository.save(order);
        poRepository.updateStatus(order.getId(), PurchaseOrderStatus.PLACED);
        order.setStatus(PurchaseOrderStatus.PLACED);

        // Receive goods: 10 units for product1 (stock becomes 30), 10 units for product2 (stock becomes 40)
        receipt = receiptRepository.recordReceipt(order.getId(), "REC-01", "Delivery 1", List.of(
                new ReceiptLineInput(order.getItems().get(0).getId(), 10),
                new ReceiptLineInput(order.getItems().get(1).getId(), 10)
        ));

        receiptItem1 = receipt.getItems().get(0);
        receiptItem2 = receipt.getItems().get(1);
    }

    @Test
    @DisplayName("Should execute valid full return and reduce product stock")
    void shouldExecuteValidFullReturn() {
        assertEquals(30, productRepository.findBySku("SKU-RET-1").orElseThrow().getQuantity());

        ReturnLineInput line = new ReturnLineInput(receiptItem1.getId(), 10, "Defective batch");
        SupplierReturn ret = returnRepository.recordReturn(order.getId(), "RET-1001", "Returning all hammers", List.of(line));

        assertNotNull(ret);
        assertEquals("RET-1001", ret.getReturnNumber());
        assertEquals(1, ret.getItems().size());
        assertEquals(10, ret.getTotalReturnedUnits());

        SupplierReturnItem item = ret.getItems().get(0);
        assertEquals(receiptItem1.getId(), item.getPurchaseOrderReceiptItemId());
        assertEquals(product1.getId(), item.getProductId());
        assertEquals(10, item.getQuantity());
        assertEquals("Defective batch", item.getReason());
        assertNotNull(item.getInventoryTransactionId());

        // Product stock decreased from 30 back to 20
        assertEquals(20, productRepository.findBySku("SKU-RET-1").orElseThrow().getQuantity());

        // Verify STOCK_OUT transaction
        List<InventoryTransaction> txs = transactionRepository.findByProductId(product1.getId());
        InventoryTransaction stockOutTx = txs.stream()
                .filter(t -> t.getId().equals(item.getInventoryTransactionId()))
                .findFirst().orElseThrow();
        assertEquals(TransactionType.STOCK_OUT, stockOutTx.getType());
        assertEquals(10, stockOutTx.getQuantity());

        // Returnable quantity should now be 0
        Map<Long, Integer> returnedMap = returnRepository.getReturnedQuantitiesForOrder(order.getId());
        assertEquals(10, returnedMap.get(receiptItem1.getId()));
    }

    @Test
    @DisplayName("Should execute partial return and allow subsequent return up to remaining quantity")
    void shouldExecutePartialReturnsMultipleTimes() {
        // Return 4 units
        ReturnLineInput line1 = new ReturnLineInput(receiptItem1.getId(), 4, "Damaged packaging");
        returnRepository.recordReturn(order.getId(), "RET-PART-1", "Return 1", List.of(line1));

        // Stock decreased 30 -> 26
        assertEquals(26, productRepository.findBySku("SKU-RET-1").orElseThrow().getQuantity());

        // Return 6 more units (completing the 10 units received)
        ReturnLineInput line2 = new ReturnLineInput(receiptItem1.getId(), 6, "Wrong specifications");
        returnRepository.recordReturn(order.getId(), "RET-PART-2", "Return 2", List.of(line2));

        // Stock decreased 26 -> 20
        assertEquals(20, productRepository.findBySku("SKU-RET-1").orElseThrow().getQuantity());

        // Attempting to return 1 more unit should fail because returnable is now 0
        ReturnLineInput line3 = new ReturnLineInput(receiptItem1.getId(), 1, "Excess return");
        ValidationException ex = assertThrows(ValidationException.class, () ->
                returnRepository.recordReturn(order.getId(), "RET-PART-3", "Return 3", List.of(line3)));
        assertTrue(ex.getMessage().contains("exceeds returnable quantity"));
    }

    @Test
    @DisplayName("Should reject return quantity exceeding returnable quantity")
    void shouldRejectQuantityExceedingReturnable() {
        ReturnLineInput line = new ReturnLineInput(receiptItem1.getId(), 15, "Too many");
        ValidationException ex = assertThrows(ValidationException.class, () ->
                returnRepository.recordReturn(order.getId(), "RET-EXCEED", "Fail", List.of(line)));
        assertTrue(ex.getMessage().contains("exceeds returnable quantity"));
    }

    @Test
    @DisplayName("Should reject return when current available stock is insufficient")
    void shouldRejectWhenCurrentStockIsInsufficient() {
        // Product 1 currently has 30 in stock.
        // Simulate external stock-out of 25 units (e.g. customer sales), leaving only 5 in stock.
        transactionRepository.recordMovement(product1.getId(), TransactionType.STOCK_OUT, 25);
        assertEquals(5, productRepository.findBySku("SKU-RET-1").orElseThrow().getQuantity());

        // Even though 10 units were received from supplier, only 5 units exist in warehouse!
        ReturnLineInput line = new ReturnLineInput(receiptItem1.getId(), 6, "Cannot fulfill return");
        ValidationException ex = assertThrows(ValidationException.class, () ->
                returnRepository.recordReturn(order.getId(), "RET-STOCK-FAIL", "Fail", List.of(line)));
        assertTrue(ex.getMessage().contains("Insufficient stock"));
    }

    @Test
    @DisplayName("Should reject returns against DRAFT or CANCELLED orders")
    void shouldRejectReturnsForDraftOrCancelledOrders() {
        PurchaseOrder draftOrder = new PurchaseOrder("PO-DRAFT-RET", supplier.getId());
        poRepository.save(draftOrder);

        ReturnLineInput line = new ReturnLineInput(receiptItem1.getId(), 1, "Invalid");
        assertThrows(ValidationException.class, () ->
                returnRepository.recordReturn(draftOrder.getId(), "RET-DRAFT", "Fail", List.of(line)));
    }

    @Test
    @DisplayName("Should reject non-existent or unrelated receipt lines")
    void shouldRejectNonExistentReceiptLine() {
        ReturnLineInput line = new ReturnLineInput(9999L, 1, "Ghost line");
        assertThrows(ValidationException.class, () ->
                returnRepository.recordReturn(order.getId(), "RET-GHOST", "Fail", List.of(line)));
    }

    @Test
    @DisplayName("Should reject duplicate return numbers")
    void shouldRejectDuplicateReturnNumbers() {
        ReturnLineInput line1 = new ReturnLineInput(receiptItem1.getId(), 2, "First");
        returnRepository.recordReturn(order.getId(), "RET-DUP", "First", List.of(line1));

        ReturnLineInput line2 = new ReturnLineInput(receiptItem1.getId(), 2, "Second");
        assertThrows(ValidationException.class, () ->
                returnRepository.recordReturn(order.getId(), "RET-DUP", "Second", List.of(line2)));
    }

    @Test
    @DisplayName("Should find returns by order ID and return number")
    void shouldFindReturnsByOrderIdAndNumber() {
        ReturnLineInput line1 = new ReturnLineInput(receiptItem1.getId(), 2, "Damaged");
        returnRepository.recordReturn(order.getId(), "RET-FIND-1", "First return", List.of(line1));

        ReturnLineInput line2 = new ReturnLineInput(receiptItem2.getId(), 3, "Wrong size");
        returnRepository.recordReturn(order.getId(), "RET-FIND-2", "Second return", List.of(line2));

        List<SupplierReturn> returns = returnRepository.findReturnsByOrderId(order.getId());
        assertEquals(2, returns.size());

        Optional<SupplierReturn> found = returnRepository.findByReturnNumber("RET-FIND-1");
        assertTrue(found.isPresent());
        assertEquals("RET-FIND-1", found.get().getReturnNumber());
        assertEquals(2, found.get().getTotalReturnedUnits());
    }

    @Test
    @DisplayName("Should check if supplier return references product ID")
    void shouldCheckProductReference() {
        assertFalse(returnRepository.existsByProductId(product1.getId()));

        ReturnLineInput line = new ReturnLineInput(receiptItem1.getId(), 2, "Damaged");
        returnRepository.recordReturn(order.getId(), "RET-PROD-REF", "Reason", List.of(line));

        assertTrue(returnRepository.existsByProductId(product1.getId()));
        assertFalse(returnRepository.existsByProductId(9999L));
    }
}
