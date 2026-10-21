package com.stockflow.reliability;

import com.stockflow.exception.ValidationException;
import com.stockflow.model.InventoryTransaction;
import com.stockflow.model.Product;
import com.stockflow.model.PurchaseOrder;
import com.stockflow.model.PurchaseOrderItem;
import com.stockflow.model.PurchaseOrderStatus;
import com.stockflow.model.ReceiptLineInput;
import com.stockflow.model.ReturnLineInput;
import com.stockflow.model.Supplier;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 16: Business Invariants Review Tests")
class BusinessInvariantsTest {

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
    @DisplayName("Invariant 1: SKU and Supplier Code uniqueness must be strictly enforced")
    void shouldEnforceIdentifierUniqueness() {
        productService.createProduct("SKU-INV-1", "Product 1", new BigDecimal("10.00"), 5, 1);
        assertThrows(ValidationException.class, () ->
                productService.createProduct("SKU-INV-1", "Duplicate Product", new BigDecimal("15.00"), 10, 2)
        );

        supplierService.createSupplier("SUP-INV-1", "Supplier 1", null, null, null, null);
        assertThrows(ValidationException.class, () ->
                supplierService.createSupplier("SUP-INV-1", "Duplicate Supplier", null, null, null, null)
        );
    }

    @Test
    @DisplayName("Invariant 2: Product quantity can never become negative")
    void shouldPreventNegativeProductQuantity() {
        assertThrows(IllegalArgumentException.class, () ->
                new Product("SKU-INV-NEG", "Negative Product", new BigDecimal("10.00"), -1, 0)
        );

        productService.createProduct("SKU-INV-STOCK", "Stock Product", new BigDecimal("10.00"), 5, 1);
        Product product = productService.findProductBySku("SKU-INV-STOCK").orElseThrow();

        // Attempting to stock out 6 when only 5 exist
        assertThrows(ValidationException.class, () ->
                productService.stockOut(product.getSku(), 6)
        );

        assertEquals(5, productService.findProductBySku("SKU-INV-STOCK").orElseThrow().getQuantity());
    }

    @Test
    @DisplayName("Invariant 3: Selling price and purchasing unit cost must remain independent")
    void shouldKeepSellingPriceAndUnitCostIndependent() {
        supplierService.createSupplier("SUP-INV-COST", "Supplier Cost", null, null, null, null);
        productService.createProduct("SKU-INV-COST", "Cost Test Product", new BigDecimal("100.00"), 10, 2);

        PurchaseOrder order = poService.createDraftOrder("PO-INV-COST", "SUP-INV-COST");
        poService.addOrUpdateItem("PO-INV-COST", "SKU-INV-COST", 5, new BigDecimal("60.00"));

        // Update selling price to $120.00
        productService.updateProduct("SKU-INV-COST", "Updated Product", new BigDecimal("120.00"), 2);

        // Verify product price is 120.00 but PO item cost remains unchanged at 60.00
        Product p = productService.findProductBySku("SKU-INV-COST").orElseThrow();
        assertEquals(new BigDecimal("120.00"), p.getPrice());

        PurchaseOrder fetchedOrder = poService.getOrderByNumber("PO-INV-COST").orElseThrow();
        PurchaseOrderItem item = fetchedOrder.getItems().get(0);
        assertEquals(new BigDecimal("60.00"), item.getUnitCost());
    }

    @Test
    @DisplayName("Invariant 4: Purchase order creation and placement must never modify inventory stock")
    void shouldNotChangeStockOnOrderCreationOrPlacement() {
        supplierService.createSupplier("SUP-INV-PO", "Supplier PO", null, null, null, null);
        productService.createProduct("SKU-INV-PO", "PO Test Product", new BigDecimal("50.00"), 25, 5);

        PurchaseOrder order = poService.createDraftOrder("PO-INV-TEST", "SUP-INV-PO");
        poService.addOrUpdateItem("PO-INV-TEST", "SKU-INV-PO", 50, new BigDecimal("40.00"));
        assertEquals(25, productService.findProductBySku("SKU-INV-PO").orElseThrow().getQuantity());

        poService.placeOrder("PO-INV-TEST");
        assertEquals(25, productService.findProductBySku("SKU-INV-PO").orElseThrow().getQuantity());
        assertTrue(transactionRepository.findByProductId(productService.findProductBySku("SKU-INV-PO").orElseThrow().getId()).isEmpty());
    }

    @Test
    @DisplayName("Invariant 5: Receiving cannot exceed ordered quantities")
    void shouldPreventReceivingExceedingOrdered() {
        supplierService.createSupplier("SUP-INV-RCV", "Supplier Rcv", null, null, null, null);
        productService.createProduct("SKU-INV-RCV", "Rcv Product", new BigDecimal("20.00"), 0, 0);

        poService.createDraftOrder("PO-INV-RCV", "SUP-INV-RCV");
        poService.addOrUpdateItem("PO-INV-RCV", "SKU-INV-RCV", 10, new BigDecimal("15.00"));
        poService.placeOrder("PO-INV-RCV");

        PurchaseOrder order = poService.getOrderByNumber("PO-INV-RCV").orElseThrow();
        long itemId = order.getItems().get(0).getId();

        // Attempt receiving 11 when 10 was ordered
        assertThrows(ValidationException.class, () ->
                poService.receiveGoods("PO-INV-RCV", List.of(new ReceiptLineInput(itemId, 11)), "Over-receiving")
        );

        // Receive 6
        poService.receiveGoods("PO-INV-RCV", List.of(new ReceiptLineInput(itemId, 6)), "First delivery");
        assertEquals(PurchaseOrderStatus.PARTIALLY_RECEIVED, poService.getOrderByNumber("PO-INV-RCV").orElseThrow().getStatus());

        // Attempt receiving 5 when only 4 remaining
        assertThrows(ValidationException.class, () ->
                poService.receiveGoods("PO-INV-RCV", List.of(new ReceiptLineInput(itemId, 5)), "Exceed remaining")
        );

        // Receive remaining 4
        poService.receiveGoods("PO-INV-RCV", List.of(new ReceiptLineInput(itemId, 4)), "Final delivery");
        assertEquals(PurchaseOrderStatus.RECEIVED, poService.getOrderByNumber("PO-INV-RCV").orElseThrow().getStatus());
    }

    @Test
    @DisplayName("Invariant 6: Returns cannot exceed previously received quantities minus earlier returns")
    void shouldPreventReturnsExceedingReturnable() {
        supplierService.createSupplier("SUP-INV-RET", "Supplier Ret", null, null, null, null);
        productService.createProduct("SKU-INV-RET", "Ret Product", new BigDecimal("30.00"), 10, 0);

        poService.createDraftOrder("PO-INV-RET", "SUP-INV-RET");
        poService.addOrUpdateItem("PO-INV-RET", "SKU-INV-RET", 8, new BigDecimal("20.00"));
        poService.placeOrder("PO-INV-RET");

        PurchaseOrder order = poService.getOrderByNumber("PO-INV-RET").orElseThrow();
        long itemId = order.getItems().get(0).getId();

        var receipt = poService.receiveGoods("PO-INV-RET", List.of(new ReceiptLineInput(itemId, 8)), "Deliver 8");
        long receiptLineId = receipt.getItems().get(0).getId();

        // Return 5
        poService.createSupplierReturn("PO-INV-RET", List.of(new ReturnLineInput(receiptLineId, 5, "Damaged")), "Return 1");

        // Attempt returning 4 when only 3 remaining
        assertThrows(ValidationException.class, () ->
                poService.createSupplierReturn("PO-INV-RET", List.of(new ReturnLineInput(receiptLineId, 4, "Too many")), "Excess return")
        );

        // Return remaining 3
        SupplierReturn ret2 = poService.createSupplierReturn("PO-INV-RET", List.of(new ReturnLineInput(receiptLineId, 3, "Scratched")), "Return 2");
        assertNotNull(ret2);

        // Attempt returning when returnable is 0
        assertThrows(ValidationException.class, () ->
                poService.createSupplierReturn("PO-INV-RET", List.of(new ReturnLineInput(receiptLineId, 1, "None left")), "Depleted")
        );
    }

    @Test
    @DisplayName("Invariant 7: Returns cannot exceed current available warehouse stock")
    void shouldPreventReturnsExceedingAvailableStock() {
        supplierService.createSupplier("SUP-INV-STOCK", "Supplier Stock", null, null, null, null);
        productService.createProduct("SKU-INV-STOCK2", "Stock Product 2", new BigDecimal("10.00"), 0, 0);

        poService.createDraftOrder("PO-INV-STOCK", "SUP-INV-STOCK");
        poService.addOrUpdateItem("PO-INV-STOCK", "SKU-INV-STOCK2", 10, new BigDecimal("8.00"));
        poService.placeOrder("PO-INV-STOCK");

        PurchaseOrder order = poService.getOrderByNumber("PO-INV-STOCK").orElseThrow();
        long itemId = order.getItems().get(0).getId();

        var receipt = poService.receiveGoods("PO-INV-STOCK", List.of(new ReceiptLineInput(itemId, 10)), "Deliver 10");
        long receiptLineId = receipt.getItems().get(0).getId();
        assertEquals(10, productService.findProductBySku("SKU-INV-STOCK2").orElseThrow().getQuantity());

        // Stock out 8 units (e.g. sold)
        productService.stockOut("SKU-INV-STOCK2", 8);
        assertEquals(2, productService.findProductBySku("SKU-INV-STOCK2").orElseThrow().getQuantity());

        // Attempt returning 5 units: 10 were received and 0 returned, but only 2 remain on hand
        assertThrows(ValidationException.class, () ->
                poService.createSupplierReturn("PO-INV-STOCK", List.of(new ReturnLineInput(receiptLineId, 5, "Defective")), "Exceeds stock")
        );

        // Return 2 units succeeds
        SupplierReturn ret = poService.createSupplierReturn("PO-INV-STOCK", List.of(new ReturnLineInput(receiptLineId, 2, "Defective")), "Valid return");
        assertNotNull(ret);
        assertEquals(0, productService.findProductBySku("SKU-INV-STOCK2").orElseThrow().getQuantity());
    }

    @Test
    @DisplayName("Invariant 8: Completed and cancelled orders obey lifecycle restrictions")
    void shouldEnforceOrderLifecycleRestrictions() {
        supplierService.createSupplier("SUP-INV-LIFE", "Supplier Lifecycle", null, null, null, null);
        productService.createProduct("SKU-INV-LIFE", "Lifecycle Product", new BigDecimal("25.00"), 10, 0);

        PurchaseOrder order = poService.createDraftOrder("PO-INV-CANCEL", "SUP-INV-LIFE");
        poService.addOrUpdateItem("PO-INV-CANCEL", "SKU-INV-LIFE", 5, new BigDecimal("20.00"));
        poService.cancelOrder("PO-INV-CANCEL");

        // Cancelled orders cannot be placed, received, modified, or returned
        assertThrows(ValidationException.class, () -> poService.placeOrder("PO-INV-CANCEL"));
        assertThrows(ValidationException.class, () -> poService.addOrUpdateItem("PO-INV-CANCEL", "SKU-INV-LIFE", 2, new BigDecimal("20.00")));
        assertThrows(ValidationException.class, () -> poService.removeItem("PO-INV-CANCEL", "SKU-INV-LIFE"));
        assertThrows(ValidationException.class, () ->
                poService.receiveGoods("PO-INV-CANCEL", List.of(new ReceiptLineInput(1L, 1)), "Receive")
        );
        assertThrows(ValidationException.class, () ->
                poService.createSupplierReturn("PO-INV-CANCEL", List.of(new ReturnLineInput(1L, 1, "Reason")), "Return")
        );
    }

    @Test
    @DisplayName("Invariant 9: Products and Suppliers referenced by historical transactions cannot be deleted")
    void shouldPreventDeletionOfReferencedEntities() {
        supplierService.createSupplier("SUP-INV-DEL", "Supplier Del", null, null, null, null);
        productService.createProduct("SKU-INV-DEL", "Product Del", new BigDecimal("15.00"), 10, 0);
        productService.assignSupplier("SKU-INV-DEL", "SUP-INV-DEL");

        // Cannot delete supplier because it is assigned to product
        assertThrows(ValidationException.class, () -> supplierService.deleteSupplierByCode("SUP-INV-DEL"));

        // Record stock transaction for product
        productService.stockIn("SKU-INV-DEL", 5);

        // Cannot delete product because it has recorded inventory transaction history
        assertThrows(ValidationException.class, () -> productService.deleteProductBySku("SKU-INV-DEL"));
    }
}
