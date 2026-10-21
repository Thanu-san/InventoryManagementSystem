package com.stockflow.reliability;

import com.stockflow.database.DatabaseConnection;
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
import com.stockflow.repository.JdbcInventoryTransactionRepository;
import com.stockflow.repository.JdbcProductRepository;
import com.stockflow.repository.JdbcPurchaseOrderReceiptRepository;
import com.stockflow.repository.JdbcPurchaseOrderReportRepository;
import com.stockflow.repository.JdbcPurchaseOrderRepository;
import com.stockflow.repository.JdbcSupplierRepository;
import com.stockflow.repository.JdbcSupplierReturnRepository;
import com.stockflow.service.ProductService;
import com.stockflow.service.PurchaseOrderService;
import com.stockflow.service.SupplierService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 16: Complete 15-Step End-to-End Workflow Test (PostgreSQL Integration)")
class EndToEndWorkflowIntegrationTest {

    private JdbcProductRepository productRepository;
    private JdbcSupplierRepository supplierRepository;
    private JdbcInventoryTransactionRepository transactionRepository;
    private JdbcPurchaseOrderRepository poRepository;
    private JdbcPurchaseOrderReceiptRepository receiptRepository;
    private JdbcPurchaseOrderReportRepository reportRepository;
    private JdbcSupplierReturnRepository returnRepository;

    private ProductService productService;
    private SupplierService supplierService;
    private PurchaseOrderService poService;

    private final List<Long> createdReturnIds = new ArrayList<>();
    private final List<Long> createdReceiptIds = new ArrayList<>();
    private final List<Long> createdPoIds = new ArrayList<>();
    private final List<String> createdProductSkus = new ArrayList<>();
    private final List<String> createdSupplierCodes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        productRepository = new JdbcProductRepository();
        supplierRepository = new JdbcSupplierRepository();
        transactionRepository = new JdbcInventoryTransactionRepository();
        poRepository = new JdbcPurchaseOrderRepository();
        receiptRepository = new JdbcPurchaseOrderReceiptRepository();
        reportRepository = new JdbcPurchaseOrderReportRepository();
        returnRepository = new JdbcSupplierReturnRepository();

        productService = new ProductService(productRepository, transactionRepository, supplierRepository, poRepository);
        supplierService = new SupplierService(supplierRepository, productRepository, poRepository);
        poService = new PurchaseOrderService(poRepository, supplierRepository, productRepository, receiptRepository, reportRepository, returnRepository);

        createdReturnIds.clear();
        createdReceiptIds.clear();
        createdPoIds.clear();
        createdProductSkus.clear();
        createdSupplierCodes.clear();

        // Ensure database tables and integrity constraints exist
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS suppliers (
                    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    supplier_code   VARCHAR(50)  NOT NULL UNIQUE,
                    name            VARCHAR(255) NOT NULL,
                    contact_name    VARCHAR(255),
                    phone           VARCHAR(50),
                    email           VARCHAR(255),
                    address         VARCHAR(500)
                );
                CREATE TABLE IF NOT EXISTS products (
                    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    sku           VARCHAR(50)    NOT NULL UNIQUE,
                    name          VARCHAR(255)   NOT NULL,
                    price         NUMERIC(12, 2) NOT NULL,
                    quantity      INT            NOT NULL DEFAULT 0,
                    minimum_stock INT            NOT NULL DEFAULT 0,
                    supplier_id   BIGINT REFERENCES suppliers(id) ON DELETE RESTRICT
                );
                CREATE TABLE IF NOT EXISTS inventory_transactions (
                    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    product_id BIGINT NOT NULL REFERENCES products(id),
                    type VARCHAR(10) NOT NULL CHECK (type IN ('STOCK_IN', 'STOCK_OUT')),
                    quantity INTEGER NOT NULL CHECK (quantity > 0),
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                );
                CREATE TABLE IF NOT EXISTS purchase_orders (
                    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    order_number VARCHAR(50) NOT NULL UNIQUE,
                    supplier_id BIGINT NOT NULL REFERENCES suppliers(id) ON DELETE RESTRICT,
                    status VARCHAR(20) NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                );
                CREATE TABLE IF NOT EXISTS purchase_order_items (
                    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    purchase_order_id BIGINT NOT NULL REFERENCES purchase_orders(id) ON DELETE RESTRICT,
                    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
                    product_sku VARCHAR(50) NOT NULL,
                    product_name VARCHAR(255) NOT NULL,
                    ordered_quantity INTEGER NOT NULL CHECK (ordered_quantity > 0),
                    unit_cost NUMERIC(12, 2) NOT NULL CHECK (unit_cost >= 0),
                    CONSTRAINT uk_po_item_order_product UNIQUE (purchase_order_id, product_id)
                );
                CREATE TABLE IF NOT EXISTS purchase_order_receipts (
                    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    receipt_number VARCHAR(50) NOT NULL UNIQUE,
                    purchase_order_id BIGINT NOT NULL REFERENCES purchase_orders(id) ON DELETE RESTRICT,
                    receipt_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    notes VARCHAR(500)
                );
                CREATE TABLE IF NOT EXISTS purchase_order_receipt_items (
                    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    receipt_id BIGINT NOT NULL REFERENCES purchase_order_receipts(id) ON DELETE RESTRICT,
                    purchase_order_item_id BIGINT NOT NULL REFERENCES purchase_order_items(id) ON DELETE RESTRICT,
                    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
                    quantity INTEGER NOT NULL CHECK (quantity > 0),
                    inventory_transaction_id BIGINT NOT NULL REFERENCES inventory_transactions(id) ON DELETE RESTRICT
                );
                CREATE TABLE IF NOT EXISTS supplier_returns (
                    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    return_number VARCHAR(50) NOT NULL UNIQUE,
                    purchase_order_id BIGINT NOT NULL REFERENCES purchase_orders(id) ON DELETE RESTRICT,
                    return_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    reason VARCHAR(500)
                );
                CREATE TABLE IF NOT EXISTS supplier_return_items (
                    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    supplier_return_id BIGINT NOT NULL REFERENCES supplier_returns(id) ON DELETE RESTRICT,
                    purchase_order_receipt_item_id BIGINT NOT NULL REFERENCES purchase_order_receipt_items(id) ON DELETE RESTRICT,
                    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
                    quantity INTEGER NOT NULL CHECK (quantity > 0),
                    reason VARCHAR(500),
                    inventory_transaction_id BIGINT NOT NULL REFERENCES inventory_transactions(id) ON DELETE RESTRICT
                );
            """);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to ensure tables exist for E2E tests", e);
        }
    }

    @AfterEach
    void tearDown() {
        try (Connection connection = DatabaseConnection.getConnection()) {
            for (Long retId : createdReturnIds) {
                try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM supplier_return_items WHERE supplier_return_id = ?")) {
                    stmt.setLong(1, retId);
                    stmt.executeUpdate();
                } catch (SQLException ignored) {}
                try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM supplier_returns WHERE id = ?")) {
                    stmt.setLong(1, retId);
                    stmt.executeUpdate();
                } catch (SQLException ignored) {}
            }
            for (Long rId : createdReceiptIds) {
                try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM purchase_order_receipt_items WHERE receipt_id = ?")) {
                    stmt.setLong(1, rId);
                    stmt.executeUpdate();
                } catch (SQLException ignored) {}
                try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM purchase_order_receipts WHERE id = ?")) {
                    stmt.setLong(1, rId);
                    stmt.executeUpdate();
                } catch (SQLException ignored) {}
            }
            for (Long poId : createdPoIds) {
                try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM purchase_order_items WHERE purchase_order_id = ?")) {
                    stmt.setLong(1, poId);
                    stmt.executeUpdate();
                } catch (SQLException ignored) {}
                try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM purchase_orders WHERE id = ?")) {
                    stmt.setLong(1, poId);
                    stmt.executeUpdate();
                } catch (SQLException ignored) {}
            }
            for (String sku : createdProductSkus) {
                try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM inventory_transactions WHERE product_id IN (SELECT id FROM products WHERE sku = ?)")) {
                    stmt.setString(1, sku);
                    stmt.executeUpdate();
                } catch (SQLException ignored) {}
                try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM products WHERE sku = ?")) {
                    stmt.setString(1, sku);
                    stmt.executeUpdate();
                } catch (SQLException ignored) {}
            }
            for (String code : createdSupplierCodes) {
                try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM suppliers WHERE supplier_code = ?")) {
                    stmt.setString(1, code);
                    stmt.executeUpdate();
                } catch (SQLException ignored) {}
            }
        } catch (SQLException e) {
            System.err.println("Error cleaning up E2E tests: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Complete 15-Step End-to-End Workflow with PostgreSQL Persistence Reconnection")
    void shouldExecute15StepWorkflowAndVerifyDatabasePersistence() {
        long timestamp = System.currentTimeMillis();
        String supplierCode = "SUP-PG-" + timestamp;
        String skuA = "SKU-PGA-" + timestamp;
        String skuB = "SKU-PGB-" + timestamp;
        String poNumber = "PO-PG-" + timestamp;

        createdSupplierCodes.add(supplierCode);
        createdProductSkus.add(skuA);
        createdProductSkus.add(skuB);

        // Step 1: Create a supplier
        Supplier supplier = supplierService.createSupplier(supplierCode, "Global Hardware Co", "Bob Miller", "555-1234", "bob@globalhw.com", "456 Industrial Blvd");
        assertNotNull(supplier.getId());

        // Step 2: Create two products
        Product prodA = productService.createProduct(skuA, "Hammer Drill", new BigDecimal("89.99"), 5, 2);
        Product prodB = productService.createProduct(skuB, "Drill Bit Set", new BigDecimal("19.99"), 20, 5);
        assertEquals(5, prodA.getQuantity());
        assertEquals(20, prodB.getQuantity());

        // Step 3: Assign the supplier to the products
        productService.assignSupplier(skuA, supplierCode);
        productService.assignSupplier(skuB, supplierCode);
        assertEquals(supplier.getId(), productService.findProductBySku(skuA).orElseThrow().getSupplierId());
        assertEquals(supplier.getId(), productService.findProductBySku(skuB).orElseThrow().getSupplierId());

        // Step 4: Create a purchase order with two product lines
        PurchaseOrder order = poService.createDraftOrder(poNumber, supplierCode);
        createdPoIds.add(order.getId());
        PurchaseOrderItem itemA = poService.addOrUpdateItem(poNumber, skuA, 10, new BigDecimal("65.00"));
        PurchaseOrderItem itemB = poService.addOrUpdateItem(poNumber, skuB, 15, new BigDecimal("12.00"));
        PurchaseOrder currentDraft = poService.getOrderByNumber(poNumber).orElseThrow();
        assertEquals(PurchaseOrderStatus.DRAFT, currentDraft.getStatus());
        assertEquals(2, currentDraft.getItems().size());

        // Step 5: Place the purchase order
        PurchaseOrder placedOrder = poService.placeOrder(poNumber);
        assertEquals(PurchaseOrderStatus.PLACED, placedOrder.getStatus());
        assertEquals(5, productService.findProductBySku(skuA).orElseThrow().getQuantity());
        assertEquals(20, productService.findProductBySku(skuB).orElseThrow().getQuantity());

        // Step 6: Receive a partial delivery (5 drills out of 10, 8 bit sets out of 15)
        PurchaseOrderReceipt partialReceipt = poService.receiveGoods(poNumber, List.of(
                new ReceiptLineInput(itemA.getId(), 5),
                new ReceiptLineInput(itemB.getId(), 8)
        ), "First partial delivery");
        assertNotNull(partialReceipt.getId());
        createdReceiptIds.add(partialReceipt.getId());

        // Step 7: Verify product quantities and STOCK_IN history
        Product prodAAfterPartial = productService.findProductBySku(skuA).orElseThrow();
        Product prodBAfterPartial = productService.findProductBySku(skuB).orElseThrow();
        assertEquals(10, prodAAfterPartial.getQuantity()); // 5 initial + 5 received = 10
        assertEquals(28, prodBAfterPartial.getQuantity()); // 20 initial + 8 received = 28

        List<InventoryTransaction> txHistoryA = transactionRepository.findByProductId(prodA.getId());
        List<InventoryTransaction> txHistoryB = transactionRepository.findByProductId(prodB.getId());
        assertEquals(1, txHistoryA.size());
        assertEquals(TransactionType.STOCK_IN, txHistoryA.get(0).getType());
        assertEquals(5, txHistoryA.get(0).getQuantity());
        assertEquals(1, txHistoryB.size());
        assertEquals(TransactionType.STOCK_IN, txHistoryB.get(0).getType());
        assertEquals(8, txHistoryB.get(0).getQuantity());

        PurchaseOrder partiallyReceivedOrder = poService.getOrderByNumber(poNumber).orElseThrow();
        assertEquals(PurchaseOrderStatus.PARTIALLY_RECEIVED, partiallyReceivedOrder.getStatus());

        // Step 8: Receive the remaining goods (5 drills and 7 bit sets)
        PurchaseOrderReceipt finalReceipt = poService.receiveGoods(poNumber, List.of(
                new ReceiptLineInput(itemA.getId(), 5),
                new ReceiptLineInput(itemB.getId(), 7)
        ), "Final delivery completion");
        assertNotNull(finalReceipt.getId());
        createdReceiptIds.add(finalReceipt.getId());

        // Step 9: Verify order status becomes RECEIVED
        PurchaseOrder fullyReceivedOrder = poService.getOrderByNumber(poNumber).orElseThrow();
        assertEquals(PurchaseOrderStatus.RECEIVED, fullyReceivedOrder.getStatus());
        assertEquals(15, productService.findProductBySku(skuA).orElseThrow().getQuantity()); // 10 + 5 = 15
        assertEquals(35, productService.findProductBySku(skuB).orElseThrow().getQuantity()); // 28 + 7 = 35

        // Step 10: Return some damaged goods (2 drills from the final receipt line)
        long receiptLineA2 = finalReceipt.getItems().stream()
                .filter(i -> i.getProductId().equals(prodA.getId()))
                .findFirst().orElseThrow().getId();

        SupplierReturn supplierReturn = poService.createSupplierReturn(poNumber, List.of(
                new ReturnLineInput(receiptLineA2, 2, "Motor seized on test")
        ), "Damaged product return");
        assertNotNull(supplierReturn.getId());
        createdReturnIds.add(supplierReturn.getId());

        // Step 11: Verify stock decreases and STOCK_OUT history is created
        assertEquals(13, productService.findProductBySku(skuA).orElseThrow().getQuantity()); // 15 - 2 = 13
        List<InventoryTransaction> txHistoryAfterReturn = transactionRepository.findByProductId(prodA.getId());
        assertEquals(3, txHistoryAfterReturn.size()); // 2 STOCK_IN + 1 STOCK_OUT
        InventoryTransaction stockOutTx = txHistoryAfterReturn.stream()
                .filter(t -> t.getType() == TransactionType.STOCK_OUT)
                .findFirst().orElseThrow();
        assertEquals(2, stockOutTx.getQuantity());

        // Step 12: Verify receipt and return histories
        List<PurchaseOrderReceipt> receipts = receiptRepository.findReceiptsByOrderId(placedOrder.getId());
        assertEquals(2, receipts.size());
        List<SupplierReturn> returns = poService.getReturnsForOrder(poNumber);
        assertEquals(1, returns.size());
        assertEquals(2, returns.get(0).getTotalReturnedUnits());

        // Step 13: Verify inventory and purchasing reports
        List<ReceivingHistoryReportItem> recvReport = poService.getReceivingHistoryReport(
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));
        assertFalse(recvReport.isEmpty());

        List<SupplierPurchasingSummaryItem> suppSummary = poService.getSupplierPurchasingSummary();
        SupplierPurchasingSummaryItem summaryItem = suppSummary.stream()
                .filter(s -> supplierCode.equals(s.getSupplierCode()))
                .findFirst().orElseThrow();
        assertEquals(1, summaryItem.getOrderCount());
        assertEquals(25, summaryItem.getTotalOrderedUnits());
        assertEquals(25, summaryItem.getTotalReceivedUnits());

        // Step 14: Verify historical prices remain unchanged
        productService.updateProduct(skuA, "Hammer Drill v2", new BigDecimal("109.99"), 2);
        PurchaseOrder orderChecked = poService.getOrderByNumber(poNumber).orElseThrow();
        for (PurchaseOrderItem itm : orderChecked.getItems()) {
            if (skuA.equals(itm.getProductSku())) {
                assertEquals(new BigDecimal("65.00"), itm.getUnitCost());
            }
        }

        // Step 15: Reconnect to PostgreSQL and verify persistence
        // Instantiate brand new repository instances (bypassing any cached state)
        JdbcProductRepository freshProductRepo = new JdbcProductRepository();
        JdbcPurchaseOrderRepository freshPoRepo = new JdbcPurchaseOrderRepository();
        JdbcPurchaseOrderReceiptRepository freshReceiptRepo = new JdbcPurchaseOrderReceiptRepository();
        JdbcSupplierReturnRepository freshReturnRepo = new JdbcSupplierReturnRepository();

        Product persistedProdA = freshProductRepo.findBySku(skuA).orElseThrow();
        Product persistedProdB = freshProductRepo.findBySku(skuB).orElseThrow();
        assertEquals(13, persistedProdA.getQuantity(), "Persisted stock for Product A must be 13");
        assertEquals(35, persistedProdB.getQuantity(), "Persisted stock for Product B must be 35");

        PurchaseOrder persistedPo = freshPoRepo.findByOrderNumber(poNumber).orElseThrow();
        assertEquals(PurchaseOrderStatus.RECEIVED, persistedPo.getStatus());
        assertEquals(2, persistedPo.getItems().size());

        List<PurchaseOrderReceipt> persistedReceipts = freshReceiptRepo.findReceiptsByOrderId(persistedPo.getId());
        assertEquals(2, persistedReceipts.size());

        List<SupplierReturn> persistedReturns = freshReturnRepo.findReturnsByOrderId(persistedPo.getId());
        assertEquals(1, persistedReturns.size());
        assertEquals(2, persistedReturns.get(0).getTotalReturnedUnits());
        assertEquals("Damaged product return", persistedReturns.get(0).getReason());
    }
}
