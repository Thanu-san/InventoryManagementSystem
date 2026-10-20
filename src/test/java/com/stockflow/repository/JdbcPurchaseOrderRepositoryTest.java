package com.stockflow.repository;

import com.stockflow.database.DatabaseConnection;
import com.stockflow.exception.ValidationException;
import com.stockflow.model.Product;
import com.stockflow.model.PurchaseOrder;
import com.stockflow.model.PurchaseOrderItem;
import com.stockflow.model.PurchaseOrderStatus;
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

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JdbcPurchaseOrderRepository Integration Tests")
class JdbcPurchaseOrderRepositoryTest {

    private JdbcPurchaseOrderRepository poRepository;
    private JdbcSupplierRepository supplierRepository;
    private JdbcProductRepository productRepository;

    private Supplier testSupplier;
    private Product testProduct;
    private Product testProduct2;

    private final List<Long> createdPoIds = new ArrayList<>();
    private final List<String> createdSupplierCodes = new ArrayList<>();
    private final List<String> createdProductSkus = new ArrayList<>();

    @BeforeEach
    void setUp() {
        poRepository = new JdbcPurchaseOrderRepository();
        supplierRepository = new JdbcSupplierRepository();
        productRepository = new JdbcProductRepository();

        createdPoIds.clear();
        createdSupplierCodes.clear();
        createdProductSkus.clear();

        // Ensure all required tables exist
        try (Connection connection = DatabaseConnection.getConnection();
             Statement stmt = connection.createStatement()) {
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
            """);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to ensure tables exist for test", e);
        }

        // Create test supplier and products
        long now = System.currentTimeMillis();
        String supCode = "PO-TEST-SUP-" + now;
        createdSupplierCodes.add(supCode);
        testSupplier = supplierRepository.save(new Supplier(supCode, "PO Test Supplier", null, null, null, null));

        String sku1 = "PO-SKU-1-" + now;
        createdProductSkus.add(sku1);
        testProduct = productRepository.save(new Product(sku1, "Test Product 1", new BigDecimal("50.00"), 100, 10));

        String sku2 = "PO-SKU-2-" + now;
        createdProductSkus.add(sku2);
        testProduct2 = productRepository.save(new Product(sku2, "Test Product 2", new BigDecimal("25.00"), 50, 5));
    }

    @AfterEach
    void tearDown() {
        try (Connection connection = DatabaseConnection.getConnection()) {
            // Delete line items first
            for (Long poId : createdPoIds) {
                try (PreparedStatement stmt = connection.prepareStatement(
                        "DELETE FROM purchase_order_items WHERE purchase_order_id = ?")) {
                    stmt.setLong(1, poId);
                    stmt.executeUpdate();
                } catch (SQLException ignored) {
                }
            }
            // Delete purchase orders
            for (Long poId : createdPoIds) {
                try (PreparedStatement stmt = connection.prepareStatement(
                        "DELETE FROM purchase_orders WHERE id = ?")) {
                    stmt.setLong(1, poId);
                    stmt.executeUpdate();
                } catch (SQLException ignored) {
                }
            }
            // Delete products
            for (String sku : createdProductSkus) {
                try (PreparedStatement stmt = connection.prepareStatement(
                        "DELETE FROM products WHERE sku = ?")) {
                    stmt.setString(1, sku);
                    stmt.executeUpdate();
                } catch (SQLException ignored) {
                }
            }
            // Delete suppliers
            for (String code : createdSupplierCodes) {
                try (PreparedStatement stmt = connection.prepareStatement(
                        "DELETE FROM suppliers WHERE supplier_code = ?")) {
                    stmt.setString(1, code);
                    stmt.executeUpdate();
                } catch (SQLException ignored) {
                }
            }
        } catch (SQLException ignored) {
        }
    }

    @Test
    @DisplayName("Should save purchase order and assign generated ID")
    void shouldSavePurchaseOrderAndAssignId() {
        String orderNum = "PO-SAVE-" + System.currentTimeMillis();
        PurchaseOrder order = new PurchaseOrder(orderNum, testSupplier.getId());

        PurchaseOrder saved = poRepository.save(order);
        assertNotNull(saved.getId());
        createdPoIds.add(saved.getId());

        assertEquals(orderNum, saved.getOrderNumber());
        assertEquals(testSupplier.getId(), saved.getSupplierId());
        assertEquals(PurchaseOrderStatus.DRAFT, saved.getStatus());
    }

    @Test
    @DisplayName("Should reject duplicate order number in PostgreSQL")
    void shouldRejectDuplicateOrderNumber() {
        String orderNum = "PO-DUP-" + System.currentTimeMillis();
        PurchaseOrder order1 = poRepository.save(new PurchaseOrder(orderNum, testSupplier.getId()));
        createdPoIds.add(order1.getId());

        ValidationException ex = assertThrows(ValidationException.class, () ->
                poRepository.save(new PurchaseOrder(orderNum, testSupplier.getId())));
        assertTrue(ex.getMessage().contains("already exists"));
    }

    @Test
    @DisplayName("Should find order by ID and order number with items loaded")
    void shouldFindOrderByIdAndOrderNumber() {
        String orderNum = "PO-FIND-" + System.currentTimeMillis();
        PurchaseOrder order = poRepository.save(new PurchaseOrder(orderNum, testSupplier.getId()));
        createdPoIds.add(order.getId());

        poRepository.saveOrUpdateItem(order.getId(),
                new PurchaseOrderItem(order.getId(), testProduct.getId(), testProduct.getSku(), testProduct.getName(), 10, new BigDecimal("45.00")));

        Optional<PurchaseOrder> byId = poRepository.findById(order.getId());
        assertTrue(byId.isPresent());
        assertEquals(1, byId.get().getItems().size());
        assertEquals(new BigDecimal("450.00"), byId.get().getTotalAmount());

        Optional<PurchaseOrder> byNumber = poRepository.findByOrderNumber(orderNum);
        assertTrue(byNumber.isPresent());
        assertEquals(order.getId(), byNumber.get().getId());
        assertEquals(1, byNumber.get().getItems().size());
    }

    @Test
    @DisplayName("Should upsert line items cleanly in PostgreSQL")
    void shouldUpsertLineItems() {
        String orderNum = "PO-UPSERT-" + System.currentTimeMillis();
        PurchaseOrder order = poRepository.save(new PurchaseOrder(orderNum, testSupplier.getId()));
        createdPoIds.add(order.getId());

        // First insert
        PurchaseOrderItem item = new PurchaseOrderItem(
                order.getId(), testProduct.getId(), testProduct.getSku(), testProduct.getName(), 5, new BigDecimal("40.00"));
        PurchaseOrderItem savedItem = poRepository.saveOrUpdateItem(order.getId(), item);
        assertNotNull(savedItem.getId());

        PurchaseOrder retrieved = poRepository.findById(order.getId()).orElseThrow();
        assertEquals(1, retrieved.getItems().size());
        assertEquals(5, retrieved.getItems().get(0).getOrderedQuantity());
        assertEquals(new BigDecimal("200.00"), retrieved.getTotalAmount());

        // Update item with new quantity and cost
        PurchaseOrderItem updatedItem = new PurchaseOrderItem(
                order.getId(), testProduct.getId(), testProduct.getSku(), testProduct.getName(), 12, new BigDecimal("38.50"));
        poRepository.saveOrUpdateItem(order.getId(), updatedItem);

        PurchaseOrder retrievedAfterUpdate = poRepository.findById(order.getId()).orElseThrow();
        assertEquals(1, retrievedAfterUpdate.getItems().size(), "Should remain single line item for product");
        assertEquals(12, retrievedAfterUpdate.getItems().get(0).getOrderedQuantity());
        assertEquals(new BigDecimal("462.00"), retrievedAfterUpdate.getTotalAmount());
    }

    @Test
    @DisplayName("Should remove line item in PostgreSQL")
    void shouldRemoveLineItem() {
        String orderNum = "PO-REMOVE-" + System.currentTimeMillis();
        PurchaseOrder order = poRepository.save(new PurchaseOrder(orderNum, testSupplier.getId()));
        createdPoIds.add(order.getId());

        poRepository.saveOrUpdateItem(order.getId(),
                new PurchaseOrderItem(order.getId(), testProduct.getId(), testProduct.getSku(), testProduct.getName(), 2, new BigDecimal("10.00")));
        poRepository.saveOrUpdateItem(order.getId(),
                new PurchaseOrderItem(order.getId(), testProduct2.getId(), testProduct2.getSku(), testProduct2.getName(), 3, new BigDecimal("5.00")));

        PurchaseOrder retrieved = poRepository.findById(order.getId()).orElseThrow();
        assertEquals(2, retrieved.getItems().size());

        boolean removed = poRepository.removeItem(order.getId(), testProduct.getId());
        assertTrue(removed);

        PurchaseOrder retrievedAfterRemoval = poRepository.findById(order.getId()).orElseThrow();
        assertEquals(1, retrievedAfterRemoval.getItems().size());
        assertEquals(testProduct2.getId(), retrievedAfterRemoval.getItems().get(0).getProductId());
    }

    @Test
    @DisplayName("Should update status in PostgreSQL")
    void shouldUpdateStatus() {
        String orderNum = "PO-STATUS-" + System.currentTimeMillis();
        PurchaseOrder order = poRepository.save(new PurchaseOrder(orderNum, testSupplier.getId()));
        createdPoIds.add(order.getId());

        assertEquals(PurchaseOrderStatus.DRAFT, poRepository.findById(order.getId()).orElseThrow().getStatus());

        boolean updated = poRepository.updateStatus(order.getId(), PurchaseOrderStatus.PLACED);
        assertTrue(updated);
        assertEquals(PurchaseOrderStatus.PLACED, poRepository.findById(order.getId()).orElseThrow().getStatus());

        poRepository.updateStatus(order.getId(), PurchaseOrderStatus.CANCELLED);
        assertEquals(PurchaseOrderStatus.CANCELLED, poRepository.findById(order.getId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("Should restrict deleting supplier when referenced by purchase orders in PostgreSQL")
    void shouldRestrictDeletingSupplierReferencedByPurchaseOrder() {
        String orderNum = "PO-FK-SUP-" + System.currentTimeMillis();
        PurchaseOrder order = poRepository.save(new PurchaseOrder(orderNum, testSupplier.getId()));
        createdPoIds.add(order.getId());

        // Deleting supplier must fail due to foreign key RESTRICT
        assertThrows(ValidationException.class, () ->
                supplierRepository.deleteBySupplierCode(testSupplier.getSupplierCode()));
    }

    @Test
    @DisplayName("Should restrict deleting product when referenced by purchase order items in PostgreSQL")
    void shouldRestrictDeletingProductReferencedByPurchaseOrderItem() {
        String orderNum = "PO-FK-PROD-" + System.currentTimeMillis();
        PurchaseOrder order = poRepository.save(new PurchaseOrder(orderNum, testSupplier.getId()));
        createdPoIds.add(order.getId());

        poRepository.saveOrUpdateItem(order.getId(),
                new PurchaseOrderItem(order.getId(), testProduct.getId(), testProduct.getSku(), testProduct.getName(), 5, new BigDecimal("10.00")));

        // Deleting product must fail due to foreign key RESTRICT
        assertThrows(ValidationException.class, () ->
                productRepository.deleteBySku(testProduct.getSku()));
    }

    @Test
    @DisplayName("Should persist purchase order data across separate connections")
    void shouldPersistAcrossSeparateConnections() {
        String orderNum = "PO-PERSIST-" + System.currentTimeMillis();
        JdbcPurchaseOrderRepository repo1 = new JdbcPurchaseOrderRepository();
        PurchaseOrder order = repo1.save(new PurchaseOrder(orderNum, testSupplier.getId()));
        createdPoIds.add(order.getId());

        repo1.saveOrUpdateItem(order.getId(),
                new PurchaseOrderItem(order.getId(), testProduct.getId(), testProduct.getSku(), testProduct.getName(), 8, new BigDecimal("12.50")));

        JdbcPurchaseOrderRepository repo2 = new JdbcPurchaseOrderRepository();
        Optional<PurchaseOrder> fromDb = repo2.findByOrderNumber(orderNum);

        assertTrue(fromDb.isPresent());
        assertEquals(1, fromDb.get().getItems().size());
        assertEquals(new BigDecimal("100.00"), fromDb.get().getTotalAmount());
    }
}
