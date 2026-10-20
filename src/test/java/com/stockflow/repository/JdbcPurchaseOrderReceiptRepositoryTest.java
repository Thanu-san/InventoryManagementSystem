package com.stockflow.repository;

import com.stockflow.database.DatabaseConnection;
import com.stockflow.exception.DatabaseException;
import com.stockflow.exception.ValidationException;
import com.stockflow.model.InventoryTransaction;
import com.stockflow.model.Product;
import com.stockflow.model.PurchaseOrder;
import com.stockflow.model.PurchaseOrderItem;
import com.stockflow.model.PurchaseOrderReceipt;
import com.stockflow.model.PurchaseOrderReceiptItem;
import com.stockflow.model.PurchaseOrderStatus;
import com.stockflow.model.ReceiptLineInput;
import com.stockflow.model.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JdbcPurchaseOrderReceiptRepository Integration Tests")
class JdbcPurchaseOrderReceiptRepositoryTest {

    private JdbcPurchaseOrderReceiptRepository receiptRepository;
    private JdbcPurchaseOrderRepository poRepository;
    private JdbcProductRepository productRepository;
    private JdbcSupplierRepository supplierRepository;

    private Supplier testSupplier;
    private Product testProduct1;
    private Product testProduct2;
    private PurchaseOrder placedOrder;
    private PurchaseOrderItem orderItem1;
    private PurchaseOrderItem orderItem2;

    private final List<Long> createdReceiptIds = new ArrayList<>();
    private final List<Long> createdPoIds = new ArrayList<>();
    private final List<String> createdProductSkus = new ArrayList<>();
    private final List<String> createdSupplierCodes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        receiptRepository = new JdbcPurchaseOrderReceiptRepository();
        poRepository = new JdbcPurchaseOrderRepository();
        productRepository = new JdbcProductRepository();
        supplierRepository = new JdbcSupplierRepository();

        createdReceiptIds.clear();
        createdPoIds.clear();
        createdProductSkus.clear();
        createdSupplierCodes.clear();

        // Ensure database schema is initialized
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
            """);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to ensure tables exist for receipt tests", e);
        }

        long now = System.currentTimeMillis();
        String supCode = "RECV-SUP-" + now;
        createdSupplierCodes.add(supCode);
        testSupplier = supplierRepository.save(new Supplier(supCode, "Receiving Supplier", null, null, null, null));

        String sku1 = "RECV-P1-" + now;
        createdProductSkus.add(sku1);
        testProduct1 = productRepository.save(new Product(sku1, "Product 1", new BigDecimal("15.00"), 50, 5));

        String sku2 = "RECV-P2-" + now;
        createdProductSkus.add(sku2);
        testProduct2 = productRepository.save(new Product(sku2, "Product 2", new BigDecimal("25.00"), 30, 5));

        String poNum = "PO-RECV-" + now;
        placedOrder = poRepository.save(new PurchaseOrder(poNum, testSupplier.getId()));
        createdPoIds.add(placedOrder.getId());

        orderItem1 = poRepository.saveOrUpdateItem(placedOrder.getId(),
                new PurchaseOrderItem(placedOrder.getId(), testProduct1.getId(), testProduct1.getSku(), testProduct1.getName(), 20, new BigDecimal("12.00")));
        orderItem2 = poRepository.saveOrUpdateItem(placedOrder.getId(),
                new PurchaseOrderItem(placedOrder.getId(), testProduct2.getId(), testProduct2.getSku(), testProduct2.getName(), 10, new BigDecimal("20.00")));

        poRepository.updateStatus(placedOrder.getId(), PurchaseOrderStatus.PLACED);
        placedOrder.setStatus(PurchaseOrderStatus.PLACED);
    }

    @AfterEach
    void tearDown() {
        try (Connection connection = DatabaseConnection.getConnection()) {
            // Delete receipt items
            for (Long rId : createdReceiptIds) {
                try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM purchase_order_receipt_items WHERE receipt_id = ?")) {
                    stmt.setLong(1, rId);
                    stmt.executeUpdate();
                } catch (SQLException ignored) {
                }
            }
            // Delete receipts
            for (Long rId : createdReceiptIds) {
                try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM purchase_order_receipts WHERE id = ?")) {
                    stmt.setLong(1, rId);
                    stmt.executeUpdate();
                } catch (SQLException ignored) {
                }
            }
            // Delete PO items & POs
            for (Long poId : createdPoIds) {
                try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM purchase_order_receipt_items WHERE purchase_order_item_id IN (SELECT id FROM purchase_order_items WHERE purchase_order_id = ?)")) {
                    stmt.setLong(1, poId);
                    stmt.executeUpdate();
                } catch (SQLException ignored) {
                }
                try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM purchase_order_items WHERE purchase_order_id = ?")) {
                    stmt.setLong(1, poId);
                    stmt.executeUpdate();
                } catch (SQLException ignored) {
                }
                try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM purchase_order_receipts WHERE purchase_order_id = ?")) {
                    stmt.setLong(1, poId);
                    stmt.executeUpdate();
                } catch (SQLException ignored) {
                }
                try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM purchase_orders WHERE id = ?")) {
                    stmt.setLong(1, poId);
                    stmt.executeUpdate();
                } catch (SQLException ignored) {
                }
            }
            // Delete transactions, products, suppliers
            for (String sku : createdProductSkus) {
                try (PreparedStatement stmt = connection.prepareStatement(
                        "DELETE FROM inventory_transactions WHERE product_id IN (SELECT id FROM products WHERE sku = ?)")) {
                    stmt.setString(1, sku);
                    stmt.executeUpdate();
                } catch (SQLException ignored) {
                }
                try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM products WHERE sku = ?")) {
                    stmt.setString(1, sku);
                    stmt.executeUpdate();
                } catch (SQLException ignored) {
                }
            }
            for (String code : createdSupplierCodes) {
                try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM suppliers WHERE supplier_code = ?")) {
                    stmt.setString(1, code);
                    stmt.executeUpdate();
                } catch (SQLException ignored) {
                }
            }
        } catch (SQLException ignored) {
        }
    }

    @Test
    @DisplayName("Should atomically record full delivery in PostgreSQL and link transactions")
    void shouldAtomicallyRecordFullDelivery() {
        String recNum = "REC-PG-FULL-" + System.currentTimeMillis();
        List<ReceiptLineInput> lines = List.of(
                new ReceiptLineInput(orderItem1.getId(), 20),
                new ReceiptLineInput(orderItem2.getId(), 10)
        );

        PurchaseOrderReceipt receipt = receiptRepository.recordReceipt(placedOrder.getId(), recNum, "PostgreSQL full delivery", lines);
        assertNotNull(receipt.getId());
        createdReceiptIds.add(receipt.getId());

        assertEquals(2, receipt.getItems().size());
        assertEquals(30, receipt.getTotalReceivedQuantity());

        // Verify product stock increased: 50 + 20 = 70, 30 + 10 = 40
        assertEquals(70, productRepository.findBySku(testProduct1.getSku()).orElseThrow().getQuantity());
        assertEquals(40, productRepository.findBySku(testProduct2.getSku()).orElseThrow().getQuantity());

        // Verify inventory transaction links
        for (PurchaseOrderReceiptItem item : receipt.getItems()) {
            assertNotNull(item.getInventoryTransactionId());
            assertTrue(item.getInventoryTransactionId() > 0);
        }

        // Verify purchase order status in database became RECEIVED
        PurchaseOrder updatedPo = poRepository.findById(placedOrder.getId()).orElseThrow();
        assertEquals(PurchaseOrderStatus.RECEIVED, updatedPo.getStatus());
        assertEquals(30, updatedPo.getTotalReceivedUnits());
        assertEquals(0, updatedPo.getTotalRemainingUnits());
    }

    @Test
    @DisplayName("Should record partial delivery and track status as PARTIALLY_RECEIVED")
    void shouldRecordPartialDelivery() {
        String recNum = "REC-PG-PART-" + System.currentTimeMillis();
        List<ReceiptLineInput> lines = List.of(
                new ReceiptLineInput(orderItem1.getId(), 8)
        );

        PurchaseOrderReceipt receipt = receiptRepository.recordReceipt(placedOrder.getId(), recNum, "Part 1", lines);
        createdReceiptIds.add(receipt.getId());

        assertEquals(58, productRepository.findBySku(testProduct1.getSku()).orElseThrow().getQuantity());
        assertEquals(30, productRepository.findBySku(testProduct2.getSku()).orElseThrow().getQuantity());

        PurchaseOrder updatedPo = poRepository.findById(placedOrder.getId()).orElseThrow();
        assertEquals(PurchaseOrderStatus.PARTIALLY_RECEIVED, updatedPo.getStatus());
        assertEquals(8, updatedPo.getTotalReceivedUnits());
        assertEquals(22, updatedPo.getTotalRemainingUnits());
    }

    @Test
    @DisplayName("Should reject delivery exceeding remaining quantity in PostgreSQL")
    void shouldRejectOverDelivery() {
        String recNum = "REC-PG-OVER-" + System.currentTimeMillis();

        ValidationException ex = assertThrows(ValidationException.class, () ->
                receiptRepository.recordReceipt(placedOrder.getId(), recNum, null,
                        List.of(new ReceiptLineInput(orderItem1.getId(), 25))));
        assertTrue(ex.getMessage().contains("exceeds remaining quantity"));

        // Product stock must remain unchanged
        assertEquals(50, productRepository.findBySku(testProduct1.getSku()).orElseThrow().getQuantity());
    }

    @Test
    @DisplayName("Should rollback all changes when atomic transaction fails mid-flight")
    void shouldRollbackAllChangesOnFailure() {
        String recNum = "REC-PG-FAIL-" + System.currentTimeMillis();

        // 1 valid line and 1 line with invalid item ID (will fail)
        List<ReceiptLineInput> lines = List.of(
                new ReceiptLineInput(orderItem1.getId(), 10),
                new ReceiptLineInput(999_999_999L, 5)
        );

        assertThrows(ValidationException.class, () ->
                receiptRepository.recordReceipt(placedOrder.getId(), recNum, null, lines));

        // Verify product 1 stock did NOT increase
        assertEquals(50, productRepository.findBySku(testProduct1.getSku()).orElseThrow().getQuantity());

        // Verify receipt was NOT saved
        assertTrue(receiptRepository.findByReceiptNumber(recNum).isEmpty());

        // Verify purchase order status remains PLACED
        assertEquals(PurchaseOrderStatus.PLACED, poRepository.findById(placedOrder.getId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("Should prevent concurrent receipts from exceeding ordered quantity")
    void shouldPreventConcurrentOverDelivery() throws InterruptedException, ExecutionException {
        // orderItem2 has 10 units. We launch 2 concurrent threads each trying to receive 8 units (total 16 > 10).
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        long now = System.currentTimeMillis();
        Callable<Void> task1 = () -> {
            try {
                PurchaseOrderReceipt r = receiptRepository.recordReceipt(
                        placedOrder.getId(), "REC-CONC-1-" + now, "T1", List.of(new ReceiptLineInput(orderItem2.getId(), 8)));
                createdReceiptIds.add(r.getId());
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            }
            return null;
        };

        Callable<Void> task2 = () -> {
            try {
                PurchaseOrderReceipt r = receiptRepository.recordReceipt(
                        placedOrder.getId(), "REC-CONC-2-" + now, "T2", List.of(new ReceiptLineInput(orderItem2.getId(), 8)));
                createdReceiptIds.add(r.getId());
                successCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            }
            return null;
        };

        List<Future<Void>> futures = executor.invokeAll(List.of(task1, task2));
        for (Future<Void> f : futures) {
            f.get();
        }
        executor.shutdown();

        // Exactly one thread must succeed, and one must fail
        assertEquals(1, successCount.get(), "Exactly one concurrent thread should succeed");
        assertEquals(1, failureCount.get(), "The other concurrent thread should fail due to insufficient remaining quantity");

        // Stock quantity should be exactly 30 + 8 = 38
        assertEquals(38, productRepository.findBySku(testProduct2.getSku()).orElseThrow().getQuantity());
    }

    @Test
    @DisplayName("Should prevent deleting product while referenced by purchase order receipt items")
    void shouldPreventDeletingProductReferencedByReceiptItem() {
        String recNum = "REC-FK-PROD-" + System.currentTimeMillis();
        PurchaseOrderReceipt receipt = receiptRepository.recordReceipt(placedOrder.getId(), recNum, null,
                List.of(new ReceiptLineInput(orderItem1.getId(), 5)));
        createdReceiptIds.add(receipt.getId());

        // Deleting the product must fail due to foreign key RESTRICT
        assertThrows(ValidationException.class, () ->
                productRepository.deleteBySku(testProduct1.getSku()));
    }
}
