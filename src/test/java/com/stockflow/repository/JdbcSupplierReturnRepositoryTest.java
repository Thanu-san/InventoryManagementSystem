package com.stockflow.repository;

import com.stockflow.database.DatabaseConnection;
import com.stockflow.exception.DatabaseException;
import com.stockflow.exception.ValidationException;
import com.stockflow.model.Product;
import com.stockflow.model.PurchaseOrder;
import com.stockflow.model.PurchaseOrderItem;
import com.stockflow.model.PurchaseOrderReceipt;
import com.stockflow.model.PurchaseOrderReceiptItem;
import com.stockflow.model.PurchaseOrderStatus;
import com.stockflow.model.ReceiptLineInput;
import com.stockflow.model.ReceiptLineReturnableInfo;
import com.stockflow.model.ReturnLineInput;
import com.stockflow.model.Supplier;
import com.stockflow.model.SupplierReturn;
import com.stockflow.model.SupplierReturnItem;
import com.stockflow.service.PurchaseOrderService;
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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JdbcSupplierReturnRepository Integration Tests")
class JdbcSupplierReturnRepositoryTest {

    private JdbcSupplierReturnRepository returnRepository;
    private JdbcPurchaseOrderReceiptRepository receiptRepository;
    private JdbcPurchaseOrderRepository poRepository;
    private JdbcProductRepository productRepository;
    private JdbcSupplierRepository supplierRepository;
    private PurchaseOrderService poService;

    private Supplier testSupplier;
    private Product testProduct;
    private PurchaseOrder placedOrder;
    private PurchaseOrderItem orderItem;
    private PurchaseOrderReceipt receipt;
    private PurchaseOrderReceiptItem receiptItem;

    private final List<Long> createdReturnIds = new ArrayList<>();
    private final List<Long> createdReceiptIds = new ArrayList<>();
    private final List<Long> createdPoIds = new ArrayList<>();
    private final List<String> createdProductSkus = new ArrayList<>();
    private final List<String> createdSupplierCodes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        returnRepository = new JdbcSupplierReturnRepository();
        receiptRepository = new JdbcPurchaseOrderReceiptRepository();
        poRepository = new JdbcPurchaseOrderRepository();
        productRepository = new JdbcProductRepository();
        supplierRepository = new JdbcSupplierRepository();
        poService = new PurchaseOrderService(poRepository, supplierRepository, productRepository, receiptRepository, null, returnRepository);

        createdReturnIds.clear();
        createdReceiptIds.clear();
        createdPoIds.clear();
        createdProductSkus.clear();
        createdSupplierCodes.clear();

        // Ensure database schema is initialized including phase 15 tables
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
            throw new RuntimeException("Failed to ensure tables exist for return tests", e);
        }

        long now = System.currentTimeMillis();
        String supCode = "RET-SUP-" + now;
        createdSupplierCodes.add(supCode);
        testSupplier = supplierRepository.save(new Supplier(supCode, "Return Supplier", null, null, null, null));

        String sku = "RET-P-" + now;
        createdProductSkus.add(sku);
        testProduct = productRepository.save(new Product(sku, "Product For Returns", new BigDecimal("45.00"), 50, 5));

        String poNum = "PO-RET-TEST-" + now;
        placedOrder = poRepository.save(new PurchaseOrder(poNum, testSupplier.getId()));
        createdPoIds.add(placedOrder.getId());

        orderItem = poRepository.saveOrUpdateItem(placedOrder.getId(),
                new PurchaseOrderItem(placedOrder.getId(), testProduct.getId(), testProduct.getSku(), testProduct.getName(), 20, new BigDecimal("30.00")));
        poRepository.updateStatus(placedOrder.getId(), PurchaseOrderStatus.PLACED);
        placedOrder.setStatus(PurchaseOrderStatus.PLACED);

        // Receive 15 units into stock
        String receiptNum = "RCV-RET-TEST-" + now;
        receipt = receiptRepository.recordReceipt(placedOrder.getId(), receiptNum, "Initial delivery for return tests", List.of(
                new ReceiptLineInput(orderItem.getId(), 15)
        ));
        createdReceiptIds.add(receipt.getId());
        receiptItem = receipt.getItems().get(0);
    }

    @AfterEach
    void tearDown() {
        try (Connection connection = DatabaseConnection.getConnection()) {
            // Delete return items
            for (Long retId : createdReturnIds) {
                try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM supplier_return_items WHERE supplier_return_id = ?")) {
                    stmt.setLong(1, retId);
                    stmt.executeUpdate();
                } catch (SQLException ignored) {
                }
            }
            // Delete returns
            for (Long retId : createdReturnIds) {
                try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM supplier_returns WHERE id = ?")) {
                    stmt.setLong(1, retId);
                    stmt.executeUpdate();
                } catch (SQLException ignored) {
                }
            }
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
                try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM purchase_order_items WHERE purchase_order_id = ?")) {
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
            // Delete products
            for (String sku : createdProductSkus) {
                try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM inventory_transactions WHERE product_id IN (SELECT id FROM products WHERE sku = ?)")) {
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
            // Delete suppliers
            for (String code : createdSupplierCodes) {
                try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM suppliers WHERE supplier_code = ?")) {
                    stmt.setString(1, code);
                    stmt.executeUpdate();
                } catch (SQLException ignored) {
                }
            }
        } catch (SQLException e) {
            System.err.println("Error cleaning up return tests: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Should query returnable receipt lines with correct initial quantities")
    void shouldQueryReturnableReceiptLines() {
        List<ReceiptLineReturnableInfo> lines = poService.getReturnableReceiptLinesForOrder(placedOrder.getOrderNumber());
        assertFalse(lines.isEmpty());
        ReceiptLineReturnableInfo line = lines.get(0);
        assertEquals(receiptItem.getId(), line.getReceiptItemId());
        assertEquals(15, line.getReceivedQuantity());
        assertEquals(0, line.getPreviouslyReturnedQuantity());
        assertEquals(15, line.getReturnableQuantity());
    }

    @Test
    @DisplayName("Should successfully save supplier return, reduce stock, and create STOCK_OUT transaction")
    void shouldSaveSupplierReturnSuccessfully() {
        String retNum = "RET-JDBC-" + System.currentTimeMillis();
        SupplierReturn ret = returnRepository.recordReturn(
                placedOrder.getId(),
                retNum,
                "Defective unit return",
                List.of(new ReturnLineInput(receiptItem.getId(), 4, "Broken parts"))
        );
        createdReturnIds.add(ret.getId());

        assertNotNull(ret.getId());
        assertEquals(retNum, ret.getReturnNumber());
        assertEquals(1, ret.getItems().size());
        SupplierReturnItem item = ret.getItems().get(0);
        assertEquals(4, item.getQuantity());
        assertEquals("Broken parts", item.getReason());
        assertNotNull(item.getInventoryTransactionId());

        // Stock was 50 + 15 = 65; after return of 4 it should be 61
        Product productInDb = productRepository.findBySku(testProduct.getSku()).orElseThrow();
        assertEquals(61, productInDb.getQuantity());

        // Returnable remaining should now be 11
        List<ReceiptLineReturnableInfo> linesAfter = poService.getReturnableReceiptLinesForOrder(placedOrder.getOrderNumber());
        assertEquals(11, linesAfter.get(0).getReturnableQuantity());
        assertEquals(4, linesAfter.get(0).getPreviouslyReturnedQuantity());
    }

    @Test
    @DisplayName("Should support multiple partial returns against the same receipt line")
    void shouldSupportMultiplePartialReturns() {
        long now = System.currentTimeMillis();
        SupplierReturn ret1 = returnRepository.recordReturn(
                placedOrder.getId(),
                "RET-P1-" + now,
                "Return 1",
                List.of(new ReturnLineInput(receiptItem.getId(), 5, "Defect 1"))
        );
        createdReturnIds.add(ret1.getId());

        SupplierReturn ret2 = returnRepository.recordReturn(
                placedOrder.getId(),
                "RET-P2-" + now,
                "Return 2",
                List.of(new ReturnLineInput(receiptItem.getId(), 3, "Defect 2"))
        );
        createdReturnIds.add(ret2.getId());

        List<SupplierReturn> returnsForOrder = returnRepository.findReturnsByOrderId(placedOrder.getId());
        assertEquals(2, returnsForOrder.size());

        Map<Long, Integer> returnedMap = returnRepository.getReturnedQuantitiesForOrder(placedOrder.getId());
        assertEquals(8, returnedMap.get(receiptItem.getId()));

        List<ReceiptLineReturnableInfo> lines = poService.getReturnableReceiptLinesForOrder(placedOrder.getOrderNumber());
        assertEquals(8, lines.get(0).getPreviouslyReturnedQuantity());
        assertEquals(7, lines.get(0).getReturnableQuantity());
    }

    @Test
    @DisplayName("Should reject return if return quantity exceeds returnable quantity")
    void shouldRejectExceedingReturnableQuantity() {
        String retNum = "RET-EXCEED-" + System.currentTimeMillis();
        ValidationException ex = assertThrows(ValidationException.class, () ->
                returnRepository.recordReturn(
                        placedOrder.getId(),
                        retNum,
                        "Exceed test",
                        List.of(new ReturnLineInput(receiptItem.getId(), 20, "Too many"))
                )
        );
        assertTrue(ex.getMessage().contains("exceeds returnable quantity"));
    }

    @Test
    @DisplayName("Should reject return if available on-hand stock is insufficient")
    void shouldRejectWhenStockInsufficient() {
        // Decrease stock directly in DB to 2
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("UPDATE products SET quantity = 2 WHERE id = ?")) {
            stmt.setLong(1, testProduct.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            fail("Failed to update stock: " + e.getMessage());
        }

        String retNum = "RET-NO-STOCK-" + System.currentTimeMillis();
        ValidationException ex = assertThrows(ValidationException.class, () ->
                returnRepository.recordReturn(
                        placedOrder.getId(),
                        retNum,
                        "Insufficient stock test",
                        List.of(new ReturnLineInput(receiptItem.getId(), 5, "Insufficient stock"))
                )
        );
        assertTrue(ex.getMessage().contains("Insufficient stock"));

        // Verify product stock was not changed
        Product product = productRepository.findBySku(testProduct.getSku()).orElseThrow();
        assertEquals(2, product.getQuantity());
    }

    @Test
    @DisplayName("Should find return by return number")
    void shouldFindReturnByNumber() {
        String retNum = "RET-FIND-" + System.currentTimeMillis();
        SupplierReturn saved = returnRepository.recordReturn(
                placedOrder.getId(),
                retNum,
                "Find test",
                List.of(new ReturnLineInput(receiptItem.getId(), 2, "Test find"))
        );
        createdReturnIds.add(saved.getId());

        Optional<SupplierReturn> foundOpt = returnRepository.findByReturnNumber(retNum);
        assertTrue(foundOpt.isPresent());
        SupplierReturn found = foundOpt.get();
        assertEquals(retNum, found.getReturnNumber());
        assertEquals(1, found.getItems().size());
        assertEquals("Test find", found.getItems().get(0).getReason());
    }

    @Test
    @DisplayName("Should rollback atomically if one line in a multi-line return fails")
    void shouldRollbackAtomicallyOnFailure() {
        // Create a second product with 0 stock
        String sku2 = "RET-P2-FAIL-" + System.currentTimeMillis();
        createdProductSkus.add(sku2);
        Product product2 = productRepository.save(new Product(sku2, "Product Zero Stock", new BigDecimal("20.00"), 0, 5));

        // Add line item to PO
        PurchaseOrderItem poItem2 = poRepository.saveOrUpdateItem(placedOrder.getId(),
                new PurchaseOrderItem(placedOrder.getId(), product2.getId(), product2.getSku(), product2.getName(), 10, new BigDecimal("15.00")));

        // Receive 5 of product2, then immediately drain product2 stock to 0
        PurchaseOrderReceipt receipt2 = receiptRepository.recordReceipt(placedOrder.getId(), "RCV-FAIL-" + System.currentTimeMillis(), "Deliver prod 2", List.of(
                new ReceiptLineInput(poItem2.getId(), 5)
        ));
        createdReceiptIds.add(receipt2.getId());

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("UPDATE products SET quantity = 0 WHERE id = ?")) {
            stmt.setLong(1, product2.getId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            fail("Failed to zero stock: " + e.getMessage());
        }

        long initialStockP1 = productRepository.findBySku(testProduct.getSku()).orElseThrow().getQuantity();

        String retNum = "RET-ATOMIC-FAIL-" + System.currentTimeMillis();
        // Line 1 is valid (testProduct), Line 2 fails because product2 has 0 stock
        assertThrows(ValidationException.class, () ->
                returnRepository.recordReturn(
                        placedOrder.getId(),
                        retNum,
                        "Atomic rollback test",
                        List.of(
                                new ReturnLineInput(receiptItem.getId(), 2, "Valid line"),
                                new ReturnLineInput(receipt2.getItems().get(0).getId(), 3, "Failing line")
                        )
                )
        );

        // Verify product 1 stock did NOT change
        Product p1After = productRepository.findBySku(testProduct.getSku()).orElseThrow();
        assertEquals(initialStockP1, p1After.getQuantity());

        // Verify no return record was saved
        assertTrue(returnRepository.findByReturnNumber(retNum).isEmpty());
    }

    @Test
    @DisplayName("Concurrent returns should serialize safely and not exceed returnable quantity")
    void shouldSafelyHandleConcurrentReturns() throws Exception {
        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Total returnable quantity is 15. Each thread attempts to return 5.
        // Exactly 3 should succeed (3 * 5 = 15), and 1 should fail.
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            tasks.add(() -> {
                try {
                    String retNum = "RET-CONC-" + System.currentTimeMillis() + "-" + index;
                    SupplierReturn ret = returnRepository.recordReturn(
                            placedOrder.getId(),
                            retNum,
                            "Concurrency test note",
                            List.of(new ReturnLineInput(receiptItem.getId(), 5, "Concurrent return test"))
                    );
                    synchronized (createdReturnIds) {
                        createdReturnIds.add(ret.getId());
                    }
                    successCount.incrementAndGet();
                } catch (ValidationException | DatabaseException e) {
                    failureCount.incrementAndGet();
                }
                return null;
            });
        }

        List<Future<Void>> futures = executor.invokeAll(tasks);
        for (Future<Void> future : futures) {
            future.get();
        }
        executor.shutdown();

        assertEquals(3, successCount.get(), "Exactly 3 returns of 5 units should succeed for 15 available units");
        assertEquals(1, failureCount.get(), "Exactly 1 return should fail due to exceeding returnable quantity");

        List<ReceiptLineReturnableInfo> lines = poService.getReturnableReceiptLinesForOrder(placedOrder.getOrderNumber());
        assertEquals(0, lines.get(0).getReturnableQuantity());
        assertEquals(15, lines.get(0).getPreviouslyReturnedQuantity());
    }
}
