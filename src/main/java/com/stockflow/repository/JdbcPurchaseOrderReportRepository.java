package com.stockflow.repository;

import com.stockflow.database.DatabaseConnection;
import com.stockflow.exception.DatabaseException;
import com.stockflow.exception.ValidationException;
import com.stockflow.model.OutstandingPurchaseOrderReportItem;
import com.stockflow.model.PurchaseOrderStatus;
import com.stockflow.model.ReceivingHistoryReportItem;
import com.stockflow.model.SupplierPurchasingSummaryItem;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * PostgreSQL JDBC implementation of PurchaseOrderReportRepository.
 * Uses CTEs and pre-aggregated subqueries to completely prevent multi-table join multiplication traps.
 */
public class JdbcPurchaseOrderReportRepository implements PurchaseOrderReportRepository {

    private static final String SELECT_OUTSTANDING_PURCHASE_ORDERS_SQL =
            "WITH item_receipts AS ( " +
            "    SELECT purchase_order_item_id, COALESCE(SUM(quantity), 0) AS item_received_qty " +
            "    FROM purchase_order_receipt_items " +
            "    GROUP BY purchase_order_item_id " +
            "), " +
            "po_item_summary AS ( " +
            "    SELECT " +
            "        poi.purchase_order_id, " +
            "        COUNT(poi.id) AS distinct_line_count, " +
            "        COALESCE(SUM(poi.ordered_quantity), 0) AS total_ordered_units, " +
            "        COALESCE(SUM(COALESCE(ir.item_received_qty, 0)), 0) AS total_received_units " +
            "    FROM purchase_order_items poi " +
            "    LEFT JOIN item_receipts ir ON poi.id = ir.purchase_order_item_id " +
            "    GROUP BY poi.purchase_order_id " +
            ") " +
            "SELECT " +
            "    po.order_number, " +
            "    s.supplier_code, " +
            "    s.name AS supplier_name, " +
            "    po.created_at, " +
            "    COALESCE(pis.distinct_line_count, 0) AS line_count, " +
            "    COALESCE(pis.total_ordered_units, 0) AS ordered_units, " +
            "    COALESCE(pis.total_received_units, 0) AS received_units, " +
            "    (COALESCE(pis.total_ordered_units, 0) - COALESCE(pis.total_received_units, 0)) AS remaining_units, " +
            "    po.status " +
            "FROM purchase_orders po " +
            "JOIN suppliers s ON po.supplier_id = s.id " +
            "LEFT JOIN po_item_summary pis ON po.id = pis.purchase_order_id " +
            "WHERE po.status IN ('PLACED', 'PARTIALLY_RECEIVED') " +
            "ORDER BY po.created_at ASC, po.order_number ASC";

    private static final String SELECT_RECEIVING_HISTORY_SQL =
            "SELECT " +
            "    r.receipt_number, " +
            "    po.order_number, " +
            "    s.name AS supplier_name, " +
            "    r.receipt_date, " +
            "    poi.product_sku, " +
            "    poi.product_name, " +
            "    ri.quantity AS received_quantity, " +
            "    poi.unit_cost AS agreed_unit_cost, " +
            "    (ri.quantity * poi.unit_cost) AS line_total_cost " +
            "FROM purchase_order_receipt_items ri " +
            "JOIN purchase_order_receipts r ON ri.receipt_id = r.id " +
            "JOIN purchase_order_items poi ON ri.purchase_order_item_id = poi.id " +
            "JOIN purchase_orders po ON r.purchase_order_id = po.id " +
            "JOIN suppliers s ON po.supplier_id = s.id " +
            "WHERE r.receipt_date >= ? AND r.receipt_date < ? " +
            "ORDER BY r.receipt_date ASC, r.receipt_number ASC, ri.id ASC";

    private static final String SELECT_SUPPLIER_PURCHASING_SUMMARY_SQL =
            "WITH item_receipts AS ( " +
            "    SELECT purchase_order_item_id, COALESCE(SUM(quantity), 0) AS item_received_qty " +
            "    FROM purchase_order_receipt_items " +
            "    GROUP BY purchase_order_item_id " +
            "), " +
            "po_item_metrics AS ( " +
            "    SELECT " +
            "        poi.purchase_order_id, " +
            "        poi.id AS item_id, " +
            "        poi.ordered_quantity, " +
            "        (poi.ordered_quantity * poi.unit_cost) AS order_item_value, " +
            "        COALESCE(ir.item_received_qty, 0) AS received_quantity, " +
            "        (COALESCE(ir.item_received_qty, 0) * poi.unit_cost) AS received_item_value " +
            "    FROM purchase_order_items poi " +
            "    LEFT JOIN item_receipts ir ON poi.id = ir.purchase_order_item_id " +
            "), " +
            "po_metrics AS ( " +
            "    SELECT " +
            "        po.id AS po_id, " +
            "        po.supplier_id, " +
            "        COALESCE(SUM(pim.ordered_quantity), 0) AS po_ordered_qty, " +
            "        COALESCE(SUM(pim.received_quantity), 0) AS po_received_qty, " +
            "        COALESCE(SUM(pim.order_item_value), 0.00) AS po_total_value, " +
            "        COALESCE(SUM(pim.received_item_value), 0.00) AS po_received_value " +
            "    FROM purchase_orders po " +
            "    LEFT JOIN po_item_metrics pim ON po.id = pim.purchase_order_id " +
            "    WHERE po.status IN ('PLACED', 'PARTIALLY_RECEIVED', 'RECEIVED') " +
            "    GROUP BY po.id, po.supplier_id " +
            "), " +
            "supplier_summary AS ( " +
            "    SELECT " +
            "        s.id AS supplier_id, " +
            "        s.supplier_code, " +
            "        s.name AS supplier_name, " +
            "        COUNT(pm.po_id) AS order_count, " +
            "        COALESCE(SUM(pm.po_ordered_qty), 0) AS total_ordered_units, " +
            "        COALESCE(SUM(pm.po_received_qty), 0) AS total_received_units, " +
            "        COALESCE(SUM(pm.po_total_value), 0.00) AS total_po_value, " +
            "        COALESCE(SUM(pm.po_received_value), 0.00) AS total_received_value " +
            "    FROM suppliers s " +
            "    LEFT JOIN po_metrics pm ON s.id = pm.supplier_id " +
            "    GROUP BY s.id, s.supplier_code, s.name " +
            ") " +
            "SELECT " +
            "    supplier_code, " +
            "    supplier_name, " +
            "    order_count, " +
            "    total_ordered_units, " +
            "    total_received_units, " +
            "    (total_ordered_units - total_received_units) AS total_outstanding_units, " +
            "    total_po_value, " +
            "    total_received_value " +
            "FROM supplier_summary " +
            "ORDER BY supplier_code ASC";

    @Override
    public List<OutstandingPurchaseOrderReportItem> getOutstandingPurchaseOrdersReport() {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_OUTSTANDING_PURCHASE_ORDERS_SQL);
             ResultSet rs = stmt.executeQuery()) {

            List<OutstandingPurchaseOrderReportItem> items = new ArrayList<>();
            while (rs.next()) {
                String orderNumber = rs.getString("order_number");
                String supplierCode = rs.getString("supplier_code");
                String supplierName = rs.getString("supplier_name");
                LocalDateTime createdAt = rs.getTimestamp("created_at").toLocalDateTime();
                int lineCount = rs.getInt("line_count");
                int orderedUnits = rs.getInt("ordered_units");
                int receivedUnits = rs.getInt("received_units");
                int remainingUnits = rs.getInt("remaining_units");
                PurchaseOrderStatus status = PurchaseOrderStatus.valueOf(rs.getString("status"));

                items.add(new OutstandingPurchaseOrderReportItem(
                        orderNumber,
                        supplierCode,
                        supplierName,
                        createdAt,
                        lineCount,
                        orderedUnits,
                        receivedUnits,
                        remainingUnits,
                        status
                ));
            }
            return Collections.unmodifiableList(items);

        } catch (SQLException e) {
            throw new DatabaseException("Failed to generate outstanding purchase orders report.", e);
        }
    }

    @Override
    public List<ReceivingHistoryReportItem> getReceivingHistoryReport(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) {
            throw new ValidationException("Start date cannot be null.");
        }
        if (endDate == null) {
            throw new ValidationException("End date cannot be null.");
        }
        if (startDate.isAfter(endDate)) {
            throw new ValidationException("Start date cannot be after end date.");
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_RECEIVING_HISTORY_SQL)) {

            // Parameterized interval: [startDate.atStartOfDay(), endDate + 1.atStartOfDay())
            stmt.setTimestamp(1, Timestamp.valueOf(startDate.atStartOfDay()));
            stmt.setTimestamp(2, Timestamp.valueOf(endDate.plusDays(1).atStartOfDay()));

            try (ResultSet rs = stmt.executeQuery()) {
                List<ReceivingHistoryReportItem> items = new ArrayList<>();
                while (rs.next()) {
                    String receiptNumber = rs.getString("receipt_number");
                    String orderNumber = rs.getString("order_number");
                    String supplierName = rs.getString("supplier_name");
                    LocalDateTime receiptDate = rs.getTimestamp("receipt_date").toLocalDateTime();
                    String sku = rs.getString("product_sku");
                    String name = rs.getString("product_name");
                    int receivedQty = rs.getInt("received_quantity");
                    BigDecimal agreedCost = rs.getBigDecimal("agreed_unit_cost");
                    BigDecimal lineCost = rs.getBigDecimal("line_total_cost");

                    items.add(new ReceivingHistoryReportItem(
                            receiptNumber,
                            orderNumber,
                            supplierName,
                            receiptDate,
                            sku,
                            name,
                            receivedQty,
                            agreedCost,
                            lineCost
                    ));
                }
                return Collections.unmodifiableList(items);
            }

        } catch (SQLException e) {
            throw new DatabaseException("Failed to generate receiving history report.", e);
        }
    }

    @Override
    public List<SupplierPurchasingSummaryItem> getSupplierPurchasingSummary() {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(SELECT_SUPPLIER_PURCHASING_SUMMARY_SQL);
             ResultSet rs = stmt.executeQuery()) {

            List<SupplierPurchasingSummaryItem> items = new ArrayList<>();
            while (rs.next()) {
                String supplierCode = rs.getString("supplier_code");
                String supplierName = rs.getString("supplier_name");
                int orderCount = rs.getInt("order_count");
                int orderedUnits = rs.getInt("total_ordered_units");
                int receivedUnits = rs.getInt("total_received_units");
                int outstandingUnits = rs.getInt("total_outstanding_units");
                BigDecimal totalOrderValue = rs.getBigDecimal("total_po_value");
                BigDecimal totalReceivedValue = rs.getBigDecimal("total_received_value");

                items.add(new SupplierPurchasingSummaryItem(
                        supplierCode,
                        supplierName,
                        orderCount,
                        orderedUnits,
                        receivedUnits,
                        outstandingUnits,
                        totalOrderValue != null ? totalOrderValue : BigDecimal.ZERO,
                        totalReceivedValue != null ? totalReceivedValue : BigDecimal.ZERO
                ));
            }
            return Collections.unmodifiableList(items);

        } catch (SQLException e) {
            throw new DatabaseException("Failed to generate supplier purchasing summary report.", e);
        }
    }
}
