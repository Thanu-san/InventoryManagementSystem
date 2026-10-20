package com.stockflow.repository;

import com.stockflow.database.DatabaseConnection;
import com.stockflow.exception.ValidationException;
import com.stockflow.model.OutstandingPurchaseOrderReportItem;
import com.stockflow.model.Product;
import com.stockflow.model.PurchaseOrder;
import com.stockflow.model.PurchaseOrderItem;
import com.stockflow.model.PurchaseOrderReceipt;
import com.stockflow.model.PurchaseOrderStatus;
import com.stockflow.model.ReceiptLineInput;
import com.stockflow.model.ReceivingHistoryReportItem;
import com.stockflow.model.Supplier;
import com.stockflow.model.SupplierPurchasingSummaryItem;
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

@DisplayName("JdbcPurchaseOrderReportRepository PostgreSQL Integration Tests")
class JdbcPurchaseOrderReportRepositoryTest {

    private JdbcPurchaseOrderReportRepository reportRepository;
    private JdbcPurchaseOrderRepository poRepository;
    private JdbcPurchaseOrderReceiptRepository receiptRepository;
    private JdbcProductRepository productRepository;
    private JdbcSupplierRepository supplierRepository;

    private final List<Long> createdReceiptIds = new ArrayList<>();
    private final List<Long> createdPoIds = new ArrayList<>();
    private final List<String> createdProductSkus = new ArrayList<>();
    private final List<String> createdSupplierCodes = new ArrayList<>();

    private Supplier supplierA;
    private Supplier supplierB;
    private Product productA;
    private Product productB;

    @BeforeEach
    void setUp() {
        reportRepository = new JdbcPurchaseOrderReportRepository();
        poRepository = new JdbcPurchaseOrderRepository();
        receiptRepository = new JdbcPurchaseOrderReceiptRepository();
        productRepository = new JdbcProductRepository();
        supplierRepository = new JdbcSupplierRepository();

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
            """);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to ensure tables exist for report integration tests", e);
        }

        long now = System.currentTimeMillis();
        String supACode = "RPT-SUP-A-" + now;
        String supBCode = "RPT-SUP-B-" + now;
        createdSupplierCodes.add(supACode);
        createdSupplierCodes.add(supBCode);

        supplierA = supplierRepository.save(new Supplier(supACode, "Report Supplier A", null, null, null, null));
        supplierB = supplierRepository.save(new Supplier(supBCode, "Report Supplier B", null, null, null, null));

        String skuA = "RPT-SKU-A-" + now;
        String skuB = "RPT-SKU-B-" + now;
        createdProductSkus.add(skuA);
        createdProductSkus.add(skuB);

        productA = productRepository.save(new Product(skuA, "Report Product A", new BigDecimal("10.00"), 100, 10));
        productB = productRepository.save(new Product(skuB, "Report Product B", new BigDecimal("20.00"), 50, 5));
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
            // Delete transactions for created products
            for (String sku : createdProductSkus) {
                try (PreparedStatement stmt = connection.prepareStatement(
                        "DELETE FROM inventory_transactions WHERE product_id IN (SELECT id FROM products WHERE sku = ?)")) {
                    stmt.setString(1, sku);
                    stmt.executeUpdate();
                } catch (SQLException ignored) {
                }
            }
            // Delete products
            for (String sku : createdProductSkus) {
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
        } catch (SQLException ignored) {
        }
    }

    @Test
    @DisplayName("Should include PLACED and PARTIALLY_RECEIVED orders and exclude DRAFT, RECEIVED, and CANCELLED")
    void shouldFilterStatusesCorrectlyInDatabase() {
        long now = System.currentTimeMillis();

        // 1. PLACED
        PurchaseOrder poPlaced = poRepository.save(new PurchaseOrder("PO-PL-" + now, supplierA.getId()));
        createdPoIds.add(poPlaced.getId());
        poRepository.saveOrUpdateItem(poPlaced.getId(), new PurchaseOrderItem(poPlaced.getId(), productA.getId(), productA.getSku(), productA.getName(), 10, new BigDecimal("15.00")));
        poRepository.updateStatus(poPlaced.getId(), PurchaseOrderStatus.PLACED);

        // 2. PARTIALLY_RECEIVED
        PurchaseOrder poPartial = poRepository.save(new PurchaseOrder("PO-PR-" + now, supplierA.getId()));
        createdPoIds.add(poPartial.getId());
        PurchaseOrderItem itemPartial = poRepository.saveOrUpdateItem(poPartial.getId(),
                new PurchaseOrderItem(poPartial.getId(), productB.getId(), productB.getSku(), productB.getName(), 20, new BigDecimal("25.00")));
        poRepository.updateStatus(poPartial.getId(), PurchaseOrderStatus.PLACED);
        PurchaseOrderReceipt r1 = receiptRepository.recordReceipt(poPartial.getId(), "REC-PR-" + now, "Part", List.of(new ReceiptLineInput(itemPartial.getId(), 5)));
        createdReceiptIds.add(r1.getId());

        // 3. DRAFT
        PurchaseOrder poDraft = poRepository.save(new PurchaseOrder("PO-DR-" + now, supplierA.getId()));
        createdPoIds.add(poDraft.getId());
        poRepository.saveOrUpdateItem(poDraft.getId(), new PurchaseOrderItem(poDraft.getId(), productA.getId(), productA.getSku(), productA.getName(), 5, new BigDecimal("15.00")));

        // 4. RECEIVED (Full)
        PurchaseOrder poRecv = poRepository.save(new PurchaseOrder("PO-RC-" + now, supplierB.getId()));
        createdPoIds.add(poRecv.getId());
        PurchaseOrderItem itemRecv = poRepository.saveOrUpdateItem(poRecv.getId(),
                new PurchaseOrderItem(poRecv.getId(), productA.getId(), productA.getSku(), productA.getName(), 8, new BigDecimal("15.00")));
        poRepository.updateStatus(poRecv.getId(), PurchaseOrderStatus.PLACED);
        PurchaseOrderReceipt r2 = receiptRepository.recordReceipt(poRecv.getId(), "REC-RC-" + now, "Full", List.of(new ReceiptLineInput(itemRecv.getId(), 8)));
        createdReceiptIds.add(r2.getId());

        // 5. CANCELLED
        PurchaseOrder poCanc = poRepository.save(new PurchaseOrder("PO-CN-" + now, supplierB.getId()));
        createdPoIds.add(poCanc.getId());
        poRepository.saveOrUpdateItem(poCanc.getId(), new PurchaseOrderItem(poCanc.getId(), productB.getId(), productB.getSku(), productB.getName(), 12, new BigDecimal("25.00")));
        poRepository.updateStatus(poCanc.getId(), PurchaseOrderStatus.CANCELLED);

        List<OutstandingPurchaseOrderReportItem> report = reportRepository.getOutstandingPurchaseOrdersReport();

        // Filter report to our test orders
        List<OutstandingPurchaseOrderReportItem> testItems = report.stream()
                .filter(i -> i.getOrderNumber().startsWith("PO-PL-" + now) ||
                             i.getOrderNumber().startsWith("PO-PR-" + now) ||
                             i.getOrderNumber().startsWith("PO-DR-" + now) ||
                             i.getOrderNumber().startsWith("PO-RC-" + now) ||
                             i.getOrderNumber().startsWith("PO-CN-" + now))
                .toList();

        assertEquals(2, testItems.size());
        assertTrue(testItems.stream().anyMatch(i -> i.getOrderNumber().equals("PO-PL-" + now) && i.getStatus() == PurchaseOrderStatus.PLACED && i.getRemainingUnits() == 10));
        assertTrue(testItems.stream().anyMatch(i -> i.getOrderNumber().equals("PO-PR-" + now) && i.getStatus() == PurchaseOrderStatus.PARTIALLY_RECEIVED && i.getRemainingUnits() == 15));
    }

    @Test
    @DisplayName("Should prevent join multiplication trap when multiple receipts exist for a line")
    void shouldPreventMultiplicationTrapInPostgres() {
        long now = System.currentTimeMillis();

        PurchaseOrder po = poRepository.save(new PurchaseOrder("PO-TRAP-" + now, supplierA.getId()));
        createdPoIds.add(po.getId());

        PurchaseOrderItem item1 = poRepository.saveOrUpdateItem(po.getId(),
                new PurchaseOrderItem(po.getId(), productA.getId(), productA.getSku(), productA.getName(), 10, new BigDecimal("20.00")));
        PurchaseOrderItem item2 = poRepository.saveOrUpdateItem(po.getId(),
                new PurchaseOrderItem(po.getId(), productB.getId(), productB.getSku(), productB.getName(), 5, new BigDecimal("40.00")));
        poRepository.updateStatus(po.getId(), PurchaseOrderStatus.PLACED);

        // Record 2 partial receipts for item 1
        PurchaseOrderReceipt r1 = receiptRepository.recordReceipt(po.getId(), "REC-T1-" + now, "Del 1", List.of(new ReceiptLineInput(item1.getId(), 3)));
        createdReceiptIds.add(r1.getId());
        PurchaseOrderReceipt r2 = receiptRepository.recordReceipt(po.getId(), "REC-T2-" + now, "Del 2", List.of(new ReceiptLineInput(item1.getId(), 4)));
        createdReceiptIds.add(r2.getId());

        // Record 1 partial receipt for item 2
        PurchaseOrderReceipt r3 = receiptRepository.recordReceipt(po.getId(), "REC-T3-" + now, "Del 3", List.of(new ReceiptLineInput(item2.getId(), 2)));
        createdReceiptIds.add(r3.getId());

        // Check Outstanding report
        OutstandingPurchaseOrderReportItem outstanding = reportRepository.getOutstandingPurchaseOrdersReport().stream()
                .filter(i -> i.getOrderNumber().equals("PO-TRAP-" + now))
                .findFirst().orElseThrow();

        assertEquals(2, outstanding.getDistinctLineCount());
        assertEquals(15, outstanding.getOrderedUnits());   // 10 + 5 (NOT multiplied!)
        assertEquals(9, outstanding.getReceivedUnits());    // 3 + 4 + 2 = 9
        assertEquals(6, outstanding.getRemainingUnits());   // 15 - 9 = 6

        // Check Supplier summary
        SupplierPurchasingSummaryItem summary = reportRepository.getSupplierPurchasingSummary().stream()
                .filter(s -> s.getSupplierCode().equals(supplierA.getSupplierCode()))
                .findFirst().orElseThrow();

        assertEquals(1, summary.getOrderCount());
        assertEquals(15, summary.getTotalOrderedUnits());
        assertEquals(9, summary.getTotalReceivedUnits());
        assertEquals(6, summary.getTotalOutstandingUnits());
        // Ordered PO value: 10 * 20.00 + 5 * 40.00 = 200.00 + 200.00 = 400.00
        assertEquals(0, new BigDecimal("400.00").compareTo(summary.getTotalOrderValue()));
        // Received value: (3 + 4) * 20.00 + 2 * 40.00 = 140.00 + 80.00 = 220.00
        assertEquals(0, new BigDecimal("220.00").compareTo(summary.getTotalReceivedValue()));
    }

    @Test
    @DisplayName("Should query receiving history accurately by date range and use historical agreed unit costs")
    void shouldQueryReceivingHistoryByDateRangeAndUseHistoricalCosts() {
        long now = System.currentTimeMillis();

        PurchaseOrder po = poRepository.save(new PurchaseOrder("PO-HIST-DB-" + now, supplierA.getId()));
        createdPoIds.add(po.getId());
        PurchaseOrderItem item = poRepository.saveOrUpdateItem(po.getId(),
                new PurchaseOrderItem(po.getId(), productA.getId(), productA.getSku(), productA.getName(), 10, new BigDecimal("12.50")));
        poRepository.updateStatus(po.getId(), PurchaseOrderStatus.PLACED);

        PurchaseOrderReceipt receipt = receiptRepository.recordReceipt(po.getId(), "REC-HIST-DB-" + now, "History Note",
                List.of(new ReceiptLineInput(item.getId(), 6)));
        createdReceiptIds.add(receipt.getId());

        // Modify product selling price to verify report uses historical snapshot cost
        productA.setPrice(new BigDecimal("99.99"));
        productRepository.update(productA);

        LocalDate today = LocalDate.now();

        // 1. In-range query
        List<ReceivingHistoryReportItem> inRange = reportRepository.getReceivingHistoryReport(today, today);
        ReceivingHistoryReportItem reportItem = inRange.stream()
                .filter(r -> r.getReceiptNumber().equals("REC-HIST-DB-" + now))
                .findFirst().orElseThrow();

        assertEquals("PO-HIST-DB-" + now, reportItem.getOrderNumber());
        assertEquals(supplierA.getName(), reportItem.getSupplierName());
        assertEquals(productA.getSku(), reportItem.getProductSku());
        assertEquals(6, reportItem.getReceivedQuantity());
        assertEquals(0, new BigDecimal("12.50").compareTo(reportItem.getAgreedUnitCost()));
        assertEquals(0, new BigDecimal("75.00").compareTo(reportItem.getLineTotalCost()));

        // 2. Out-of-range queries
        List<ReceivingHistoryReportItem> pastRange = reportRepository.getReceivingHistoryReport(today.minusDays(5), today.minusDays(1));
        assertFalse(pastRange.stream().anyMatch(r -> r.getReceiptNumber().equals("REC-HIST-DB-" + now)));

        List<ReceivingHistoryReportItem> futureRange = reportRepository.getReceivingHistoryReport(today.plusDays(1), today.plusDays(5));
        assertFalse(futureRange.stream().anyMatch(r -> r.getReceiptNumber().equals("REC-HIST-DB-" + now)));

        // 3. Invalid range validation
        assertThrows(ValidationException.class, () ->
                reportRepository.getReceivingHistoryReport(today.plusDays(1), today));
    }

    @Test
    @DisplayName("Should include suppliers with 0 orders and exclude cancelled orders in supplier summary")
    void shouldHandleSupplierSummaryWithZeroOrdersAndCancelledOrders() {
        long now = System.currentTimeMillis();

        // Supplier A has 1 CANCELLED order (100 units @ $50 = $5000)
        PurchaseOrder poA = poRepository.save(new PurchaseOrder("PO-A-CANC-" + now, supplierA.getId()));
        createdPoIds.add(poA.getId());
        poRepository.saveOrUpdateItem(poA.getId(),
                new PurchaseOrderItem(poA.getId(), productA.getId(), productA.getSku(), productA.getName(), 100, new BigDecimal("50.00")));
        poRepository.updateStatus(poA.getId(), PurchaseOrderStatus.CANCELLED);

        // Supplier B has 0 orders

        List<SupplierPurchasingSummaryItem> summary = reportRepository.getSupplierPurchasingSummary();

        SupplierPurchasingSummaryItem itemA = summary.stream()
                .filter(s -> s.getSupplierCode().equals(supplierA.getSupplierCode()))
                .findFirst().orElseThrow();

        assertEquals(0, itemA.getOrderCount());
        assertEquals(0, itemA.getTotalOrderedUnits());
        assertEquals(0, itemA.getTotalReceivedUnits());
        assertEquals(0, itemA.getTotalOutstandingUnits());
        assertEquals(0, BigDecimal.ZERO.compareTo(itemA.getTotalOrderValue()));
        assertEquals(0, BigDecimal.ZERO.compareTo(itemA.getTotalReceivedValue()));

        SupplierPurchasingSummaryItem itemB = summary.stream()
                .filter(s -> s.getSupplierCode().equals(supplierB.getSupplierCode()))
                .findFirst().orElseThrow();

        assertEquals(0, itemB.getOrderCount());
        assertEquals(0, itemB.getTotalOrderedUnits());
        assertEquals(0, itemB.getTotalReceivedUnits());
        assertEquals(0, itemB.getTotalOutstandingUnits());
        assertEquals(0, BigDecimal.ZERO.compareTo(itemB.getTotalOrderValue()));
        assertEquals(0, BigDecimal.ZERO.compareTo(itemB.getTotalReceivedValue()));
    }
}
