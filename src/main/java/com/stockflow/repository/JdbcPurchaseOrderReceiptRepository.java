package com.stockflow.repository;

import com.stockflow.database.DatabaseConnection;
import com.stockflow.exception.DatabaseException;
import com.stockflow.exception.ValidationException;
import com.stockflow.model.PurchaseOrderReceipt;
import com.stockflow.model.PurchaseOrderReceiptItem;
import com.stockflow.model.PurchaseOrderStatus;
import com.stockflow.model.ReceiptLineInput;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * PostgreSQL JDBC implementation of PurchaseOrderReceiptRepository.
 * Executes atomic receiving operations across purchase_orders, products,
 * inventory_transactions, and receiving history tables.
 */
public class JdbcPurchaseOrderReceiptRepository implements PurchaseOrderReceiptRepository {

    private static final String LOCK_ORDER_FOR_UPDATE_SQL =
            "SELECT id, order_number, status FROM purchase_orders WHERE id = ? FOR UPDATE";

    private static final String LOCK_ORDER_ITEMS_FOR_UPDATE_SQL =
            "SELECT id, product_id, product_sku, ordered_quantity FROM purchase_order_items WHERE purchase_order_id = ? FOR UPDATE";

    private static final String SELECT_EXISTING_RECEIVED_SQL =
            "SELECT ri.purchase_order_item_id, COALESCE(SUM(ri.quantity), 0) AS total_received " +
            "FROM purchase_order_receipt_items ri " +
            "JOIN purchase_order_items poi ON ri.purchase_order_item_id = poi.id " +
            "WHERE poi.purchase_order_id = ? " +
            "GROUP BY ri.purchase_order_item_id";

    private static final String INSERT_RECEIPT_SQL =
            "INSERT INTO purchase_order_receipts (receipt_number, purchase_order_id, receipt_date, notes) " +
            "VALUES (?, ?, CURRENT_TIMESTAMP, ?)";

    private static final String LOCK_PRODUCT_FOR_UPDATE_SQL =
            "SELECT quantity FROM products WHERE id = ? FOR UPDATE";

    private static final String UPDATE_PRODUCT_STOCK_SQL =
            "UPDATE products SET quantity = quantity + ? WHERE id = ?";

    private static final String INSERT_TRANSACTION_SQL =
            "INSERT INTO inventory_transactions (product_id, type, quantity, created_at) " +
            "VALUES (?, 'STOCK_IN', ?, CURRENT_TIMESTAMP)";

    private static final String INSERT_RECEIPT_ITEM_SQL =
            "INSERT INTO purchase_order_receipt_items (receipt_id, purchase_order_item_id, product_id, quantity, inventory_transaction_id) " +
            "VALUES (?, ?, ?, ?, ?)";

    private static final String UPDATE_ORDER_STATUS_SQL =
            "UPDATE purchase_orders SET status = ? WHERE id = ?";

    private static final String SELECT_RECEIPTS_BY_ORDER_ID_SQL =
            "SELECT id, receipt_number, purchase_order_id, receipt_date, notes " +
            "FROM purchase_order_receipts WHERE purchase_order_id = ? ORDER BY receipt_date DESC, id DESC";

    private static final String SELECT_RECEIPT_BY_NUMBER_SQL =
            "SELECT id, receipt_number, purchase_order_id, receipt_date, notes " +
            "FROM purchase_order_receipts WHERE receipt_number = ?";

    private static final String SELECT_ITEMS_BY_RECEIPT_ID_SQL =
            "SELECT id, receipt_id, purchase_order_item_id, product_id, quantity, inventory_transaction_id " +
            "FROM purchase_order_receipt_items WHERE receipt_id = ? ORDER BY id ASC";

    @Override
    public PurchaseOrderReceipt recordReceipt(Long orderId, String receiptNumber, String notes, List<ReceiptLineInput> lines) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("Purchase order ID must be greater than zero.");
        }
        if (receiptNumber == null || receiptNumber.isBlank()) {
            throw new ValidationException("Receipt number cannot be blank.");
        }
        String cleanReceiptNumber = receiptNumber.trim();
        if (lines == null || lines.isEmpty()) {
            throw new ValidationException("Cannot record receipt with no received items.");
        }

        Connection connection = null;
        boolean originalAutoCommit = true;
        try {
            connection = DatabaseConnection.getConnection();
            originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            // 1. Lock and validate purchase order
            String currentStatusStr;
            String orderNumber;
            try (PreparedStatement lockOrderStmt = connection.prepareStatement(LOCK_ORDER_FOR_UPDATE_SQL)) {
                lockOrderStmt.setLong(1, orderId);
                try (ResultSet rs = lockOrderStmt.executeQuery()) {
                    if (!rs.next()) {
                        throw new ValidationException("Purchase order not found with ID: " + orderId);
                    }
                    orderNumber = rs.getString("order_number");
                    currentStatusStr = rs.getString("status");
                }
            }

            PurchaseOrderStatus currentStatus = PurchaseOrderStatus.valueOf(currentStatusStr);
            if (currentStatus != PurchaseOrderStatus.PLACED && currentStatus != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
                throw new ValidationException(
                        "Cannot receive goods against purchase order '" + orderNumber +
                        "' because its status is " + currentStatus + ". Only PLACED or PARTIALLY_RECEIVED orders can be received.");
            }

            // 2. Lock and load all purchase order items
            Map<Long, OrderItemInfo> orderItemsMap = new HashMap<>();
            try (PreparedStatement lockItemsStmt = connection.prepareStatement(LOCK_ORDER_ITEMS_FOR_UPDATE_SQL)) {
                lockItemsStmt.setLong(1, orderId);
                try (ResultSet rs = lockItemsStmt.executeQuery()) {
                    while (rs.next()) {
                        long itemId = rs.getLong("id");
                        long productId = rs.getLong("product_id");
                        String sku = rs.getString("product_sku");
                        int orderedQty = rs.getInt("ordered_quantity");
                        orderItemsMap.put(itemId, new OrderItemInfo(itemId, productId, sku, orderedQty));
                    }
                }
            }

            if (orderItemsMap.isEmpty()) {
                throw new ValidationException("Cannot receive goods on an empty purchase order: " + orderNumber);
            }

            // 3. Load currently received quantities per item
            Map<Long, Integer> cumulativeReceivedMap = new HashMap<>();
            try (PreparedStatement receivedStmt = connection.prepareStatement(SELECT_EXISTING_RECEIVED_SQL)) {
                receivedStmt.setLong(1, orderId);
                try (ResultSet rs = receivedStmt.executeQuery()) {
                    while (rs.next()) {
                        cumulativeReceivedMap.put(rs.getLong("purchase_order_item_id"), rs.getInt("total_received"));
                    }
                }
            }

            // 4. Validate requested delivery lines
            Set<Long> seenItemIds = new HashSet<>();
            for (ReceiptLineInput line : lines) {
                if (line == null) {
                    throw new ValidationException("Receipt line cannot be null.");
                }
                if (!seenItemIds.add(line.purchaseOrderItemId())) {
                    throw new ValidationException("Duplicate receipt line for item ID: " + line.purchaseOrderItemId());
                }
                OrderItemInfo itemInfo = orderItemsMap.get(line.purchaseOrderItemId());
                if (itemInfo == null) {
                    throw new ValidationException("Purchase order line item not found on order: " + line.purchaseOrderItemId());
                }
                if (line.quantity() <= 0) {
                    throw new ValidationException("Received quantity must be greater than zero for product: " + itemInfo.sku());
                }

                int alreadyReceived = cumulativeReceivedMap.getOrDefault(itemInfo.id(), 0);
                int remaining = Math.max(0, itemInfo.orderedQuantity() - alreadyReceived);
                if (line.quantity() > remaining) {
                    throw new ValidationException(
                            "Received quantity (" + line.quantity() + ") exceeds remaining quantity (" +
                            remaining + ") for product: " + itemInfo.sku());
                }
            }

            // 5. Insert receipt header
            long receiptId;
            LocalDateTime receiptDate;
            try (PreparedStatement insertReceiptStmt = connection.prepareStatement(INSERT_RECEIPT_SQL, Statement.RETURN_GENERATED_KEYS)) {
                insertReceiptStmt.setString(1, cleanReceiptNumber);
                insertReceiptStmt.setLong(2, orderId);
                insertReceiptStmt.setString(3, (notes == null || notes.isBlank()) ? null : notes.trim());
                insertReceiptStmt.executeUpdate();

                try (ResultSet rs = insertReceiptStmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        receiptId = rs.getLong(1);
                        receiptDate = LocalDateTime.now();
                    } else {
                        throw new SQLException("Failed to retrieve generated key for purchase order receipt.");
                    }
                }
            }

            PurchaseOrderReceipt receipt = new PurchaseOrderReceipt(receiptId, cleanReceiptNumber, orderId, receiptDate, notes, null);

            // 6. Pre-lock all involved products in strictly ascending order by ID to prevent circular-wait deadlocks
            List<Long> productIdsToLock = lines.stream()
                    .map(line -> orderItemsMap.get(line.purchaseOrderItemId()).productId())
                    .distinct()
                    .sorted()
                    .toList();

            for (Long prodId : productIdsToLock) {
                try (PreparedStatement lockProdStmt = connection.prepareStatement(LOCK_PRODUCT_FOR_UPDATE_SQL)) {
                    lockProdStmt.setLong(1, prodId);
                    try (ResultSet rs = lockProdStmt.executeQuery()) {
                        if (!rs.next()) {
                            throw new ValidationException("Product not found with ID: " + prodId);
                        }
                    }
                }
            }

            // 7. Process each received line: product stock update, inventory transaction, receipt item
            for (ReceiptLineInput line : lines) {
                OrderItemInfo itemInfo = orderItemsMap.get(line.purchaseOrderItemId());
                long productId = itemInfo.productId();
                int qty = line.quantity();

                // 7a. Verify product capacity (lock already held)
                try (PreparedStatement checkCapStmt = connection.prepareStatement("SELECT quantity FROM products WHERE id = ?")) {
                    checkCapStmt.setLong(1, productId);
                    try (ResultSet rs = checkCapStmt.executeQuery()) {
                        if (rs.next()) {
                            long currentQty = rs.getInt("quantity");
                            if (currentQty + (long) qty > Integer.MAX_VALUE) {
                                throw new ValidationException("Stock In would cause product quantity to exceed maximum capacity for SKU: " + itemInfo.sku());
                            }
                        }
                    }
                }

                // 7b. Update product stock
                try (PreparedStatement updateStockStmt = connection.prepareStatement(UPDATE_PRODUCT_STOCK_SQL)) {
                    updateStockStmt.setInt(1, qty);
                    updateStockStmt.setLong(2, productId);
                    int affected = updateStockStmt.executeUpdate();
                    if (affected != 1) {
                        throw new ValidationException("Failed to update stock for product: " + itemInfo.sku());
                    }
                }

                // 6c. Insert inventory transaction (STOCK_IN)
                long txId;
                try (PreparedStatement insertTxStmt = connection.prepareStatement(INSERT_TRANSACTION_SQL, Statement.RETURN_GENERATED_KEYS)) {
                    insertTxStmt.setLong(1, productId);
                    insertTxStmt.setInt(2, qty);
                    insertTxStmt.executeUpdate();

                    try (ResultSet rs = insertTxStmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            txId = rs.getLong(1);
                        } else {
                            throw new SQLException("Failed to retrieve generated key for inventory transaction.");
                        }
                    }
                }

                // 6d. Insert receipt item
                long receiptItemId;
                try (PreparedStatement insertItemStmt = connection.prepareStatement(INSERT_RECEIPT_ITEM_SQL, Statement.RETURN_GENERATED_KEYS)) {
                    insertItemStmt.setLong(1, receiptId);
                    insertItemStmt.setLong(2, itemInfo.id());
                    insertItemStmt.setLong(3, productId);
                    insertItemStmt.setInt(4, qty);
                    insertItemStmt.setLong(5, txId);
                    insertItemStmt.executeUpdate();

                    try (ResultSet rs = insertItemStmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            receiptItemId = rs.getLong(1);
                        } else {
                            throw new SQLException("Failed to retrieve generated key for receipt item.");
                        }
                    }
                }

                receipt.addItem(new PurchaseOrderReceiptItem(receiptItemId, receiptId, itemInfo.id(), productId, qty, txId));

                // Update cumulative tracking
                cumulativeReceivedMap.merge(itemInfo.id(), qty, Integer::sum);
            }

            // 7. Calculate new purchase order status
            boolean allFullyReceived = true;
            for (OrderItemInfo itemInfo : orderItemsMap.values()) {
                int totalRecv = cumulativeReceivedMap.getOrDefault(itemInfo.id(), 0);
                if (totalRecv < itemInfo.orderedQuantity()) {
                    allFullyReceived = false;
                    break;
                }
            }

            PurchaseOrderStatus newStatus = allFullyReceived ? PurchaseOrderStatus.RECEIVED : PurchaseOrderStatus.PARTIALLY_RECEIVED;
            try (PreparedStatement updateStatusStmt = connection.prepareStatement(UPDATE_ORDER_STATUS_SQL)) {
                updateStatusStmt.setString(1, newStatus.name());
                updateStatusStmt.setLong(2, orderId);
                updateStatusStmt.executeUpdate();
            }

            // 8. Commit entire atomic delivery
            connection.commit();
            return receipt;

        } catch (SQLException e) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackEx) {
                    e.addSuppressed(rollbackEx);
                }
            }
            if ("23505".equals(e.getSQLState())) {
                throw new ValidationException("Receipt already exists with receipt number: " + cleanReceiptNumber, e);
            }
            if ("23503".equals(e.getSQLState()) || "23001".equals(e.getSQLState())) {
                throw new ValidationException("Referenced order, product, or item does not exist or violates integrity constraint.", e);
            }
            throw new DatabaseException("Database error during receiving operation: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackEx) {
                    e.addSuppressed(rollbackEx);
                }
            }
            throw e;
        } finally {
            if (connection != null) {
                try {
                    connection.setAutoCommit(originalAutoCommit);
                    connection.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    @Override
    public List<PurchaseOrderReceipt> findReceiptsByOrderId(Long orderId) {
        if (orderId == null || orderId <= 0) {
            return Collections.emptyList();
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement stmt = connection.prepareStatement(SELECT_RECEIPTS_BY_ORDER_ID_SQL)) {

            stmt.setLong(1, orderId);
            try (ResultSet rs = stmt.executeQuery()) {
                List<PurchaseOrderReceipt> receipts = new ArrayList<>();
                while (rs.next()) {
                    receipts.add(mapRowToReceipt(rs));
                }
                for (PurchaseOrderReceipt r : receipts) {
                    loadItemsForReceipt(connection, r);
                }
                return receipts;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Database error while retrieving receipts for order ID: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<PurchaseOrderReceipt> findByReceiptNumber(String receiptNumber) {
        if (receiptNumber == null || receiptNumber.isBlank()) {
            return Optional.empty();
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement stmt = connection.prepareStatement(SELECT_RECEIPT_BY_NUMBER_SQL)) {

            stmt.setString(1, receiptNumber.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    PurchaseOrderReceipt receipt = mapRowToReceipt(rs);
                    loadItemsForReceipt(connection, receipt);
                    return Optional.of(receipt);
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Database error while retrieving receipt by number: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<Long, Integer> getReceivedQuantitiesForOrder(Long orderId) {
        if (orderId == null || orderId <= 0) {
            return Collections.emptyMap();
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement stmt = connection.prepareStatement(SELECT_EXISTING_RECEIVED_SQL)) {

            stmt.setLong(1, orderId);
            try (ResultSet rs = stmt.executeQuery()) {
                Map<Long, Integer> map = new HashMap<>();
                while (rs.next()) {
                    map.put(rs.getLong("purchase_order_item_id"), rs.getInt("total_received"));
                }
                return map;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Database error while retrieving received quantities for order: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<Long, Map<Long, Integer>> getReceivedQuantitiesForOrders(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Map<Long, Integer>> result = new HashMap<>();
        for (Long id : orderIds) {
            result.put(id, getReceivedQuantitiesForOrder(id));
        }
        return result;
    }

    // --- Private Helper Methods ---

    private PurchaseOrderReceipt mapRowToReceipt(ResultSet rs) throws SQLException {
        Long id = rs.getLong("id");
        String receiptNumber = rs.getString("receipt_number");
        Long purchaseOrderId = rs.getLong("purchase_order_id");
        Timestamp ts = rs.getTimestamp("receipt_date");
        LocalDateTime receiptDate = ts != null ? ts.toLocalDateTime() : LocalDateTime.now();
        String notes = rs.getString("notes");

        return new PurchaseOrderReceipt(id, receiptNumber, purchaseOrderId, receiptDate, notes, null);
    }

    private void loadItemsForReceipt(Connection connection, PurchaseOrderReceipt receipt) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(SELECT_ITEMS_BY_RECEIPT_ID_SQL)) {
            stmt.setLong(1, receipt.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    receipt.addItem(new PurchaseOrderReceiptItem(
                            rs.getLong("id"),
                            rs.getLong("receipt_id"),
                            rs.getLong("purchase_order_item_id"),
                            rs.getLong("product_id"),
                            rs.getInt("quantity"),
                            rs.getLong("inventory_transaction_id")
                    ));
                }
            }
        }
    }

    private record OrderItemInfo(long id, long productId, String sku, int orderedQuantity) {}
}
