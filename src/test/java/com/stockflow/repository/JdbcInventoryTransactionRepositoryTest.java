package com.stockflow.repository;

import com.stockflow.database.DatabaseConnection;
import com.stockflow.exception.ValidationException;
import com.stockflow.model.InventoryTransaction;
import com.stockflow.model.Product;
import com.stockflow.model.TransactionType;
import com.stockflow.service.ProductService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JdbcInventoryTransactionRepository Integration Tests")
class JdbcInventoryTransactionRepositoryTest {

    private JdbcProductRepository productRepository;
    private JdbcInventoryTransactionRepository transactionRepository;

    private Product testProduct;
    private String testSku;

    @BeforeAll
    static void initSchema() throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS inventory_transactions (
                    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    product_id BIGINT NOT NULL REFERENCES products(id),
                    type VARCHAR(10) NOT NULL CHECK (type IN ('STOCK_IN', 'STOCK_OUT')),
                    quantity INTEGER NOT NULL CHECK (quantity > 0),
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
            """);
        }
    }

    @BeforeEach
    void setUp() {
        productRepository = new JdbcProductRepository();
        transactionRepository = new JdbcInventoryTransactionRepository();

        testSku = "TX-SKU-" + System.currentTimeMillis();
        testProduct = productRepository.save(new Product(
                testSku,
                "Transaction Test Product",
                new BigDecimal("49.99"),
                20,
                5
        ));
    }

    @AfterEach
    void tearDown() {
        dropTriggerQuietly();
        if (testProduct != null && testProduct.getId() != null) {
            try (Connection conn = DatabaseConnection.getConnection()) {
                // Delete child transactions first, then delete parent product
                try (PreparedStatement delTx = conn.prepareStatement("DELETE FROM inventory_transactions WHERE product_id = ?")) {
                    delTx.setLong(1, testProduct.getId());
                    delTx.executeUpdate();
                }
                try (PreparedStatement delProd = conn.prepareStatement("DELETE FROM products WHERE id = ?")) {
                    delProd.setLong(1, testProduct.getId());
                    delProd.executeUpdate();
                }
            } catch (SQLException ignored) {
            }
        }
    }

    private void dropTriggerQuietly() {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TRIGGER IF EXISTS trg_test_fail_tx_insert ON inventory_transactions");
            stmt.execute("DROP FUNCTION IF EXISTS test_fail_tx_insert()");
        } catch (SQLException ignored) {
        }
    }

    @Test
    @DisplayName("Should successfully record STOCK_IN, increment quantity, and persist transaction")
    void shouldRecordStockInSuccessfully() {
        InventoryTransaction tx = transactionRepository.recordMovement(
                testProduct.getId(),
                TransactionType.STOCK_IN,
                15
        );

        assertNotNull(tx.getId(), "Transaction ID should be generated");
        assertEquals(testProduct.getId(), tx.getProductId());
        assertEquals(TransactionType.STOCK_IN, tx.getType());
        assertEquals(15, tx.getQuantity());
        assertNotNull(tx.getCreatedAt());

        Product reloaded = productRepository.findBySku(testSku).orElseThrow();
        assertEquals(35, reloaded.getQuantity(), "Product quantity should increase from 20 to 35");

        List<InventoryTransaction> history = transactionRepository.findByProductId(testProduct.getId());
        assertEquals(1, history.size());
        assertEquals(tx.getId(), history.get(0).getId());
    }

    @Test
    @DisplayName("Should successfully record STOCK_OUT, decrement quantity, and persist transaction")
    void shouldRecordStockOutSuccessfully() {
        InventoryTransaction tx = transactionRepository.recordMovement(
                testProduct.getId(),
                TransactionType.STOCK_OUT,
                8
        );

        assertNotNull(tx.getId());
        assertEquals(testProduct.getId(), tx.getProductId());
        assertEquals(TransactionType.STOCK_OUT, tx.getType());
        assertEquals(8, tx.getQuantity());

        Product reloaded = productRepository.findBySku(testSku).orElseThrow();
        assertEquals(12, reloaded.getQuantity(), "Product quantity should decrease from 20 to 12");
    }

    @Test
    @DisplayName("Should allow STOCK_OUT reducing quantity to exactly zero")
    void shouldAllowStockOutToZero() {
        InventoryTransaction tx = transactionRepository.recordMovement(
                testProduct.getId(),
                TransactionType.STOCK_OUT,
                20
        );

        assertNotNull(tx);
        Product reloaded = productRepository.findBySku(testSku).orElseThrow();
        assertEquals(0, reloaded.getQuantity(), "Product quantity should be exactly 0");
    }

    @Test
    @DisplayName("Should reject STOCK_OUT when quantity exceeds stock and modify neither table")
    void shouldRejectStockOutExceedingAvailableStock() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                transactionRepository.recordMovement(testProduct.getId(), TransactionType.STOCK_OUT, 25));

        assertTrue(ex.getMessage().contains("Insufficient stock"));

        // Verify product quantity is untouched
        Product reloaded = productRepository.findBySku(testSku).orElseThrow();
        assertEquals(20, reloaded.getQuantity(), "Product quantity must remain unchanged");

        // Verify no transaction record was inserted
        List<InventoryTransaction> history = transactionRepository.findByProductId(testProduct.getId());
        assertTrue(history.isEmpty(), "No transaction should be recorded on failure");
    }

    @Test
    @DisplayName("Should reject movement for nonexistent product ID")
    void shouldRejectMovementForNonexistentProductId() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                transactionRepository.recordMovement(999999L, TransactionType.STOCK_IN, 10));

        assertTrue(ex.getMessage().contains("Product not found"));
    }

    @Test
    @DisplayName("Should rollback quantity update when transaction-history INSERT fails")
    void shouldRollbackQuantityUpdateWhenTransactionInsertFails() throws SQLException {
        // 1. Initial product has quantity 20
        assertEquals(20, testProduct.getQuantity());

        // 2. Install an isolated trigger targeting ONLY this specific product ID
        // so that the INSERT into inventory_transactions fails after products UPDATE succeeded
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(String.format("""
                CREATE OR REPLACE FUNCTION test_fail_tx_insert() RETURNS TRIGGER AS $$
                BEGIN
                    IF NEW.product_id = %d THEN
                        RAISE EXCEPTION 'Simulated deliberate failure on inventory_transactions INSERT for rollback test';
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql;
            """, testProduct.getId()));

            stmt.execute("""
                CREATE TRIGGER trg_test_fail_tx_insert
                BEFORE INSERT ON inventory_transactions
                FOR EACH ROW EXECUTE FUNCTION test_fail_tx_insert();
            """);
        }

        try {
            // 3. Attempt Stock Out of 5 units.
            // The UPDATE products SET quantity = quantity - 5 succeeds (row updated to 15 in transaction),
            // then INSERT into inventory_transactions fires the trigger and raises an exception.
            // 4. Verify that the operation fails.
            RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                    transactionRepository.recordMovement(testProduct.getId(), TransactionType.STOCK_OUT, 5));

            assertTrue(thrown.getMessage().contains("Simulated deliberate failure") ||
                            (thrown.getCause() != null && thrown.getCause().getMessage().contains("Simulated deliberate failure")),
                    "Exception must originate from the deliberate trigger failure");

            // 5. Reload the product from PostgreSQL and confirm its quantity is still 20 (rolled back)
            Product reloaded = productRepository.findBySku(testSku).orElseThrow();
            assertEquals(20, reloaded.getQuantity(),
                    "Product quantity in PostgreSQL must be rolled back to 20, proving quantity update was reverted");

            // 6. Verify that no transaction record was persisted for the failed movement
            List<InventoryTransaction> transactions = transactionRepository.findByProductId(testProduct.getId());
            assertTrue(transactions.isEmpty(), "No transaction history should be recorded for the aborted movement");

        } finally {
            // 7. Clean up test trigger objects safely
            dropTriggerQuietly();
        }
    }

    @Test
    @DisplayName("Should prevent deleting product in PostgreSQL when transaction history exists via repository")
    void shouldPreventDeletingProductWhenTransactionHistoryExistsViaRepository() {
        transactionRepository.recordMovement(testProduct.getId(), TransactionType.STOCK_IN, 5);

        ValidationException ex = assertThrows(ValidationException.class, () ->
                productRepository.deleteBySku(testSku));
        assertTrue(ex.getMessage().contains("Cannot delete product"));

        assertTrue(productRepository.findBySku(testSku).isPresent(),
                "Product should not be deleted from PostgreSQL");
    }

    @Test
    @DisplayName("Should prevent deleting product in PostgreSQL when transaction history exists via ProductService")
    void shouldPreventDeletingProductWhenTransactionHistoryExistsViaService() {
        ProductService service = new ProductService(productRepository, transactionRepository);
        service.stockIn(testSku, 5);

        ValidationException ex = assertThrows(ValidationException.class, () ->
                service.deleteProductBySku(testSku));
        assertTrue(ex.getMessage().contains("recorded inventory transaction history"));

        assertTrue(productRepository.findBySku(testSku).isPresent(),
                "Product should remain in PostgreSQL");
    }

    @Test
    @DisplayName("Should prevent overselling under concurrent stock-out requests")
    void shouldPreventOversellingUnderConcurrency() throws InterruptedException, ExecutionException {
        // Initial stock is 20.
        // We launch 4 concurrent threads, each attempting to stock out 10 items.
        // Exactly 2 threads should succeed (2 * 10 = 20), and 2 should fail with Insufficient stock.
        // Final stock MUST be 0 and never negative.
        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                try {
                    transactionRepository.recordMovement(testProduct.getId(), TransactionType.STOCK_OUT, 10);
                    successCount.incrementAndGet();
                } catch (ValidationException e) {
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

        assertEquals(2, successCount.get(), "Exactly 2 stock-out requests should succeed");
        assertEquals(2, failureCount.get(), "Exactly 2 stock-out requests should fail");

        Product reloaded = productRepository.findBySku(testSku).orElseThrow();
        assertEquals(0, reloaded.getQuantity(), "Final quantity must be exactly 0, never negative");

        List<InventoryTransaction> transactions = transactionRepository.findByProductId(testProduct.getId());
        assertEquals(2, transactions.size(), "Only 2 transaction records should exist");
    }

    @Test
    @DisplayName("Should retrieve transactions ordered by created_at DESC, id DESC")
    void shouldRetrieveTransactionsInDescendingOrder() throws InterruptedException {
        InventoryTransaction tx1 = transactionRepository.recordMovement(testProduct.getId(), TransactionType.STOCK_IN, 5);
        Thread.sleep(20);
        InventoryTransaction tx2 = transactionRepository.recordMovement(testProduct.getId(), TransactionType.STOCK_OUT, 3);
        Thread.sleep(20);
        InventoryTransaction tx3 = transactionRepository.recordMovement(testProduct.getId(), TransactionType.STOCK_IN, 10);

        List<InventoryTransaction> history = transactionRepository.findByProductId(testProduct.getId());
        assertEquals(3, history.size());

        assertEquals(tx3.getId(), history.get(0).getId());
        assertEquals(tx2.getId(), history.get(1).getId());
        assertEquals(tx1.getId(), history.get(2).getId());

        assertTrue(history.get(0).getId() > history.get(1).getId());
        assertTrue(history.get(1).getId() > history.get(2).getId());
    }

    @Test
    @DisplayName("Should retrieve all transactions across products ordered by created_at DESC, id DESC")
    void shouldFindAllTransactionsInOrder() {
        String testSku2 = "TX-SKU-2-" + System.currentTimeMillis();
        Product product2 = productRepository.save(new Product(
                testSku2,
                "Product 2",
                new BigDecimal("15.00"),
                10,
                2
        ));

        try {
            transactionRepository.recordMovement(testProduct.getId(), TransactionType.STOCK_IN, 5);
            transactionRepository.recordMovement(product2.getId(), TransactionType.STOCK_IN, 7);

            List<InventoryTransaction> all = transactionRepository.findAll();
            assertFalse(all.isEmpty());

            // Check that the list is descending by id/timestamp
            for (int i = 0; i < all.size() - 1; i++) {
                InventoryTransaction current = all.get(i);
                InventoryTransaction next = all.get(i + 1);
                assertTrue(
                        current.getCreatedAt().isAfter(next.getCreatedAt()) ||
                        current.getCreatedAt().isEqual(next.getCreatedAt()) ||
                        current.getId() > next.getId(),
                        "Transactions must be ordered by created_at DESC, id DESC"
                );
            }
        } finally {
            try (Connection conn = DatabaseConnection.getConnection()) {
                try (PreparedStatement delTx = conn.prepareStatement("DELETE FROM inventory_transactions WHERE product_id = ?")) {
                    delTx.setLong(1, product2.getId());
                    delTx.executeUpdate();
                }
                try (PreparedStatement delProd = conn.prepareStatement("DELETE FROM products WHERE id = ?")) {
                    delProd.setLong(1, product2.getId());
                    delProd.executeUpdate();
                }
            } catch (SQLException ignored) {
            }
        }
    }
}
