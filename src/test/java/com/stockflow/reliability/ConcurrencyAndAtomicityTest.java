package com.stockflow.reliability;

import com.stockflow.database.DatabaseConnection;
import com.stockflow.exception.DatabaseException;
import com.stockflow.exception.ValidationException;
import com.stockflow.model.Product;
import com.stockflow.model.PurchaseOrder;
import com.stockflow.model.PurchaseOrderItem;
import com.stockflow.model.PurchaseOrderReceipt;
import com.stockflow.model.PurchaseOrderStatus;
import com.stockflow.model.ReceiptLineInput;
import com.stockflow.model.ReturnLineInput;
import com.stockflow.model.Supplier;
import com.stockflow.model.SupplierReturn;
import com.stockflow.model.TransactionType;
import com.stockflow.repository.JdbcInventoryTransactionRepository;
import com.stockflow.repository.JdbcProductRepository;
import com.stockflow.repository.JdbcPurchaseOrderReceiptRepository;
import com.stockflow.repository.JdbcPurchaseOrderRepository;
import com.stockflow.repository.JdbcSupplierRepository;
import com.stockflow.repository.JdbcSupplierReturnRepository;
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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 16: Concurrency Safety and Transaction Atomicity Tests (PostgreSQL)")
class ConcurrencyAndAtomicityTest {

    private JdbcProductRepository productRepository;
    private JdbcSupplierRepository supplierRepository;
    private JdbcInventoryTransactionRepository transactionRepository;
    private JdbcPurchaseOrderRepository poRepository;
    private JdbcPurchaseOrderReceiptRepository receiptRepository;
    private JdbcSupplierReturnRepository returnRepository;

    private final List<Long> createdReturnIds = Collections.synchronizedList(new ArrayList<>());
    private final List<Long> createdReceiptIds = Collections.synchronizedList(new ArrayList<>());
    private final List<Long> createdPoIds = Collections.synchronizedList(new ArrayList<>());
    private final List<String> createdProductSkus = Collections.synchronizedList(new ArrayList<>());
    private final List<String> createdSupplierCodes = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() {
        productRepository = new JdbcProductRepository();
        supplierRepository = new JdbcSupplierRepository();
        transactionRepository = new JdbcInventoryTransactionRepository();
        poRepository = new JdbcPurchaseOrderRepository();
        receiptRepository = new JdbcPurchaseOrderReceiptRepository();
        returnRepository = new JdbcSupplierReturnRepository();

        createdReturnIds.clear();
        createdReceiptIds.clear();
        createdPoIds.clear();
        createdProductSkus.clear();
        createdSupplierCodes.clear();

        // Ensure database tables exist
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
            throw new RuntimeException("Failed to ensure tables exist for concurrency tests", e);
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
            System.err.println("Error cleaning up concurrency tests: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Concurrent Receiving: Multiple threads cannot exceed total ordered quantity")
    void concurrentReceivingShouldNeverExceedOrderedQuantity() throws Exception {
        long now = System.currentTimeMillis();
        String supCode = "SUP-CONC-RCV-" + now;
        String sku = "SKU-CONC-RCV-" + now;
        String poNum = "PO-CONC-RCV-" + now;

        createdSupplierCodes.add(supCode);
        createdProductSkus.add(sku);

        Supplier sup = supplierRepository.save(new Supplier(supCode, "Conc Rcv Sup", null, null, null, null));
        Product prod = productRepository.save(new Product(sku, "Conc Rcv Prod", new BigDecimal("10.00"), 0, 0));

        PurchaseOrder po = poRepository.save(new PurchaseOrder(poNum, sup.getId()));
        createdPoIds.add(po.getId());

        PurchaseOrderItem item = poRepository.saveOrUpdateItem(po.getId(),
                new PurchaseOrderItem(po.getId(), prod.getId(), prod.getSku(), prod.getName(), 20, new BigDecimal("8.00")));
        poRepository.updateStatus(po.getId(), PurchaseOrderStatus.PLACED);

        // 4 threads attempt to receive 10 units each concurrently.
        // Total ordered = 20. Exactly 2 threads must succeed (2 * 10 = 20) and 2 must fail.
        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            tasks.add(() -> {
                try {
                    String rcvNum = "RCV-CONC-" + now + "-" + idx;
                    PurchaseOrderReceipt rcv = receiptRepository.recordReceipt(po.getId(), rcvNum, "Concurrent rcv test", List.of(
                            new ReceiptLineInput(item.getId(), 10)
                    ));
                    createdReceiptIds.add(rcv.getId());
                    successCount.incrementAndGet();
                } catch (ValidationException | DatabaseException e) {
                    failureCount.incrementAndGet();
                }
                return null;
            });
        }

        List<Future<Void>> futures = executor.invokeAll(tasks);
        for (Future<Void> f : futures) {
            f.get();
        }
        executor.shutdown();

        assertEquals(2, successCount.get(), "Exactly 2 receipts of 10 units should succeed for 20 ordered units");
        assertEquals(2, failureCount.get(), "Exactly 2 attempts should fail due to exceeding ordered quantity");

        // Verify product stock equals exactly 20
        Product updatedProd = productRepository.findBySku(sku).orElseThrow();
        assertEquals(20, updatedProd.getQuantity());

        // Verify PO status is RECEIVED
        PurchaseOrder updatedPo = poRepository.findById(po.getId()).orElseThrow();
        assertEquals(PurchaseOrderStatus.RECEIVED, updatedPo.getStatus());
    }

    @Test
    @DisplayName("Concurrent Returns: Multiple threads cannot exceed returnable quantity")
    void concurrentReturnsShouldNeverExceedReturnableQuantity() throws Exception {
        long now = System.currentTimeMillis();
        String supCode = "SUP-CONC-RET-" + now;
        String sku = "SKU-CONC-RET-" + now;
        String poNum = "PO-CONC-RET-" + now;

        createdSupplierCodes.add(supCode);
        createdProductSkus.add(sku);

        Supplier sup = supplierRepository.save(new Supplier(supCode, "Conc Ret Sup", null, null, null, null));
        Product prod = productRepository.save(new Product(sku, "Conc Ret Prod", new BigDecimal("15.00"), 50, 0));

        PurchaseOrder po = poRepository.save(new PurchaseOrder(poNum, sup.getId()));
        createdPoIds.add(po.getId());

        PurchaseOrderItem item = poRepository.saveOrUpdateItem(po.getId(),
                new PurchaseOrderItem(po.getId(), prod.getId(), prod.getSku(), prod.getName(), 15, new BigDecimal("12.00")));
        poRepository.updateStatus(po.getId(), PurchaseOrderStatus.PLACED);

        PurchaseOrderReceipt rcv = receiptRepository.recordReceipt(po.getId(), "RCV-FOR-RET-" + now, "Deliver 15", List.of(
                new ReceiptLineInput(item.getId(), 15)
        ));
        createdReceiptIds.add(rcv.getId());
        long receiptItemId = rcv.getItems().get(0).getId();

        // 4 threads attempt to return 5 units each concurrently.
        // Returnable = 15. Exactly 3 threads must succeed (3 * 5 = 15) and 1 must fail.
        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            tasks.add(() -> {
                try {
                    String retNum = "RET-CONC-" + now + "-" + idx;
                    SupplierReturn ret = returnRepository.recordReturn(po.getId(), retNum, "Concurrent return test", List.of(
                            new ReturnLineInput(receiptItemId, 5, "Defective")
                    ));
                    createdReturnIds.add(ret.getId());
                    successCount.incrementAndGet();
                } catch (ValidationException | DatabaseException e) {
                    failureCount.incrementAndGet();
                }
                return null;
            });
        }

        List<Future<Void>> futures = executor.invokeAll(tasks);
        for (Future<Void> f : futures) {
            f.get();
        }
        executor.shutdown();

        assertEquals(3, successCount.get(), "Exactly 3 returns of 5 units should succeed for 15 returnable units");
        assertEquals(1, failureCount.get(), "Exactly 1 attempt should fail due to exceeding returnable quantity");

        // Product stock: 50 initial + 15 received - 15 returned = 50
        Product updatedProd = productRepository.findBySku(sku).orElseThrow();
        assertEquals(50, updatedProd.getQuantity());
    }

    @Test
    @DisplayName("Concurrent Stock Out: Cannot produce negative inventory")
    void concurrentStockOutShouldNeverProduceNegativeStock() throws Exception {
        long now = System.currentTimeMillis();
        String sku = "SKU-CONC-STOCK-" + now;
        createdProductSkus.add(sku);

        Product prod = productRepository.save(new Product(sku, "Conc Stock Prod", new BigDecimal("5.00"), 10, 0));

        // 4 threads attempt to stock out 4 units each concurrently.
        // Available = 10. Exactly 2 threads must succeed (2 * 4 = 8, 2 remaining) and 2 must fail.
        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                try {
                    transactionRepository.recordMovement(prod.getId(), TransactionType.STOCK_OUT, 4);
                    successCount.incrementAndGet();
                } catch (ValidationException | DatabaseException e) {
                    failureCount.incrementAndGet();
                }
                return null;
            });
        }

        List<Future<Void>> futures = executor.invokeAll(tasks);
        for (Future<Void> f : futures) {
            f.get();
        }
        executor.shutdown();

        assertEquals(2, successCount.get(), "Exactly 2 stock out requests of 4 units should succeed for 10 units");
        assertEquals(2, failureCount.get(), "Exactly 2 requests should fail due to insufficient stock");

        Product updatedProd = productRepository.findBySku(sku).orElseThrow();
        assertEquals(2, updatedProd.getQuantity());
        assertTrue(updatedProd.getQuantity() >= 0, "Product quantity must never be negative");
    }

    @Test
    @DisplayName("Deterministic Lock Ordering: Multi-line receiving with inverted item order avoids deadlocks")
    void invertedOrderMultiLineReceivingShouldNotDeadlock() throws Exception {
        long now = System.currentTimeMillis();
        String supCode = "SUP-DEADLOCK-" + now;
        String sku1 = "SKU-DL1-" + now;
        String sku2 = "SKU-DL2-" + now;

        createdSupplierCodes.add(supCode);
        createdProductSkus.add(sku1);
        createdProductSkus.add(sku2);

        Supplier sup = supplierRepository.save(new Supplier(supCode, "Deadlock Test Sup", null, null, null, null));
        Product prod1 = productRepository.save(new Product(sku1, "Product 1", new BigDecimal("10.00"), 0, 0));
        Product prod2 = productRepository.save(new Product(sku2, "Product 2", new BigDecimal("20.00"), 0, 0));

        // Create PO 1 with items [prod1, prod2]
        PurchaseOrder po1 = poRepository.save(new PurchaseOrder("PO-DL1-" + now, sup.getId()));
        createdPoIds.add(po1.getId());
        PurchaseOrderItem item1A = poRepository.saveOrUpdateItem(po1.getId(),
                new PurchaseOrderItem(po1.getId(), prod1.getId(), prod1.getSku(), prod1.getName(), 100, new BigDecimal("8.00")));
        PurchaseOrderItem item1B = poRepository.saveOrUpdateItem(po1.getId(),
                new PurchaseOrderItem(po1.getId(), prod2.getId(), prod2.getSku(), prod2.getName(), 100, new BigDecimal("15.00")));
        poRepository.updateStatus(po1.getId(), PurchaseOrderStatus.PLACED);

        // Create PO 2 with items [prod2, prod1]
        PurchaseOrder po2 = poRepository.save(new PurchaseOrder("PO-DL2-" + now, sup.getId()));
        createdPoIds.add(po2.getId());
        PurchaseOrderItem item2B = poRepository.saveOrUpdateItem(po2.getId(),
                new PurchaseOrderItem(po2.getId(), prod2.getId(), prod2.getSku(), prod2.getName(), 100, new BigDecimal("15.00")));
        PurchaseOrderItem item2A = poRepository.saveOrUpdateItem(po2.getId(),
                new PurchaseOrderItem(po2.getId(), prod1.getId(), prod1.getSku(), prod1.getName(), 100, new BigDecimal("8.00")));
        poRepository.updateStatus(po2.getId(), PurchaseOrderStatus.PLACED);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        // Thread 1 receives [prod1, prod2]
        Callable<Void> task1 = () -> {
            PurchaseOrderReceipt r1 = receiptRepository.recordReceipt(po1.getId(), "RCV-DL1-" + now, "Order 1", List.of(
                    new ReceiptLineInput(item1A.getId(), 5),
                    new ReceiptLineInput(item1B.getId(), 5)
            ));
            createdReceiptIds.add(r1.getId());
            return null;
        };

        // Thread 2 receives [prod2, prod1] (inverted order!)
        Callable<Void> task2 = () -> {
            PurchaseOrderReceipt r2 = receiptRepository.recordReceipt(po2.getId(), "RCV-DL2-" + now, "Order 2", List.of(
                    new ReceiptLineInput(item2B.getId(), 5),
                    new ReceiptLineInput(item2A.getId(), 5)
            ));
            createdReceiptIds.add(r2.getId());
            return null;
        };

        List<Future<Void>> futures = executor.invokeAll(List.of(task1, task2));
        for (Future<Void> f : futures) {
            f.get(); // Will throw ExecutionException if deadlock occurs
        }
        executor.shutdown();

        Product updatedP1 = productRepository.findBySku(sku1).orElseThrow();
        Product updatedP2 = productRepository.findBySku(sku2).orElseThrow();
        assertEquals(10, updatedP1.getQuantity(), "5 + 5 = 10 units received for Product 1");
        assertEquals(10, updatedP2.getQuantity(), "5 + 5 = 10 units received for Product 2");
    }
}
