package com.stockflow.repository;

import com.stockflow.database.DatabaseConnection;
import com.stockflow.exception.DatabaseException;
import com.stockflow.exception.ValidationException;
import com.stockflow.model.PurchaseOrderStatus;
import com.stockflow.model.ReturnLineInput;
import com.stockflow.model.SupplierReturn;
import com.stockflow.model.SupplierReturnItem;

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
 * PostgreSQL JDBC implementation of SupplierReturnRepository.
 * Executes atomic return operations across purchase_orders, purchase_order_receipts,
 * products, inventory_transactions, and supplier_returns tables using row-level locking.
 */
public class JdbcSupplierReturnRepository implements SupplierReturnRepository {

    private static final String LOCK_ORDER_FOR_UPDATE_SQL =
            "SELECT id, order_number, status FROM purchase_orders WHERE id = ? FOR UPDATE";

    private static final String LOCK_RECEIPT_ITEM_SQL =
            "SELECT ri.id, ri.receipt_id, ri.purchase_order_item_id, ri.product_id, ri.quantity, r.purchase_order_id, p.sku " +
            "FROM purchase_order_receipt_items ri " +
            "JOIN purchase_order_receipts r ON ri.receipt_id = r.id " +
            "JOIN products p ON ri.product_id = p.id " +
            "WHERE ri.id = ? FOR UPDATE OF ri";

    private static final String SELECT_EXISTING_RETURNED_FOR_ORDER_SQL =
            "SELECT sri.purchase_order_receipt_item_id, COALESCE(SUM(sri.quantity), 0) AS total_returned " +
            "FROM supplier_return_items sri " +
            "JOIN purchase_order_receipt_items ri ON sri.purchase_order_receipt_item_id = ri.id " +
            "JOIN purchase_order_receipts r ON ri.receipt_id = r.id " +
            "WHERE r.purchase_order_id = ? " +
            "GROUP BY sri.purchase_order_receipt_item_id";

    private static final String LOCK_PRODUCT_FOR_UPDATE_SQL =
            "SELECT quantity, sku FROM products WHERE id = ? FOR UPDATE";

    private static final String UPDATE_PRODUCT_STOCK_SQL =
            "UPDATE products SET quantity = quantity - ? WHERE id = ? AND quantity >= ?";

    private static final String INSERT_TRANSACTION_SQL =
            "INSERT INTO inventory_transactions (product_id, type, quantity, created_at) " +
            "VALUES (?, 'STOCK_OUT', ?, CURRENT_TIMESTAMP)";

    private static final String INSERT_RETURN_SQL =
            "INSERT INTO supplier_returns (return_number, purchase_order_id, return_date, reason) " +
            "VALUES (?, ?, CURRENT_TIMESTAMP, ?)";

    private static final String INSERT_RETURN_ITEM_SQL =
            "INSERT INTO supplier_return_items (supplier_return_id, purchase_order_receipt_item_id, product_id, quantity, reason, inventory_transaction_id) " +
            "VALUES (?, ?, ?, ?, ?, ?)";

    private static final String SELECT_RETURNS_BY_ORDER_ID_SQL =
            "SELECT id, return_number, purchase_order_id, return_date, reason " +
            "FROM supplier_returns WHERE purchase_order_id = ? ORDER BY return_date DESC, id DESC";

    private static final String SELECT_RETURN_BY_NUMBER_SQL =
            "SELECT id, return_number, purchase_order_id, return_date, reason " +
            "FROM supplier_returns WHERE return_number = ?";

    private static final String SELECT_RETURN_BY_ID_SQL =
            "SELECT id, return_number, purchase_order_id, return_date, reason " +
            "FROM supplier_returns WHERE id = ?";

    private static final String SELECT_ITEMS_BY_RETURN_ID_SQL =
            "SELECT id, supplier_return_id, purchase_order_receipt_item_id, product_id, quantity, reason, inventory_transaction_id " +
            "FROM supplier_return_items WHERE supplier_return_id = ? ORDER BY id ASC";

    private static final String EXISTS_BY_RETURN_NUMBER_SQL =
            "SELECT EXISTS (SELECT 1 FROM supplier_returns WHERE return_number = ?)";

    private static final String EXISTS_BY_PRODUCT_ID_SQL =
            "SELECT EXISTS (SELECT 1 FROM supplier_return_items WHERE product_id = ?)";

    private record ReceiptItemInfo(long id, long purchaseOrderId, long productId, String sku, int originalQty) {}

    @Override
    public SupplierReturn recordReturn(Long orderId, String returnNumber, String reason, List<ReturnLineInput> lines) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("Purchase order ID must be greater than zero.");
        }
        if (returnNumber == null || returnNumber.isBlank()) {
            throw new ValidationException("Return number cannot be blank.");
        }
        String cleanReturnNumber = returnNumber.trim();
        if (lines == null || lines.isEmpty()) {
            throw new ValidationException("Cannot record return with no return line items.");
        }

        Connection connection = null;
        boolean originalAutoCommit = true;
        try {
            connection = DatabaseConnection.getConnection();
            originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            // 1. Lock and validate purchase order
            String orderNumber;
            String statusStr;
            try (PreparedStatement lockOrderStmt = connection.prepareStatement(LOCK_ORDER_FOR_UPDATE_SQL)) {
                lockOrderStmt.setLong(1, orderId);
                try (ResultSet rs = lockOrderStmt.executeQuery()) {
                    if (!rs.next()) {
                        throw new ValidationException("Purchase order not found with ID: " + orderId);
                    }
                    orderNumber = rs.getString("order_number");
                    statusStr = rs.getString("status");
                }
            }

            PurchaseOrderStatus status = PurchaseOrderStatus.valueOf(statusStr);
            if (status == PurchaseOrderStatus.DRAFT) {
                throw new ValidationException("Cannot return goods against DRAFT purchase order: " + orderNumber);
            }
            if (status == PurchaseOrderStatus.CANCELLED) {
                throw new ValidationException("Cannot return goods against CANCELLED purchase order: " + orderNumber);
            }

            // 2. Lock and load requested receipt items in strictly ascending order by receipt item ID
            Map<Long, ReceiptItemInfo> receiptItemMap = new HashMap<>();
            Set<Long> seenReceiptItemIds = new HashSet<>();

            List<ReturnLineInput> sortedLines = lines.stream()
                    .sorted(java.util.Comparator.comparing(ReturnLineInput::purchaseOrderReceiptItemId))
                    .toList();

            for (ReturnLineInput line : sortedLines) {
                if (line == null) {
                    throw new ValidationException("Return line cannot be null.");
                }
                if (!seenReceiptItemIds.add(line.purchaseOrderReceiptItemId())) {
                    throw new ValidationException("Duplicate return line for receipt item ID: " + line.purchaseOrderReceiptItemId());
                }
                if (line.quantity() <= 0) {
                    throw new ValidationException("Return quantity must be greater than zero.");
                }

                try (PreparedStatement lockItemStmt = connection.prepareStatement(LOCK_RECEIPT_ITEM_SQL)) {
                    lockItemStmt.setLong(1, line.purchaseOrderReceiptItemId());
                    try (ResultSet rs = lockItemStmt.executeQuery()) {
                        if (!rs.next()) {
                            throw new ValidationException("Receipt line not found with ID: " + line.purchaseOrderReceiptItemId());
                        }
                        long rOrderId = rs.getLong("purchase_order_id");
                        if (rOrderId != orderId) {
                            throw new ValidationException("Receipt line " + line.purchaseOrderReceiptItemId() +
                                    " does not belong to purchase order: " + orderNumber);
                        }
                        long itemId = rs.getLong("id");
                        long productId = rs.getLong("product_id");
                        String sku = rs.getString("sku");
                        int origQty = rs.getInt("quantity");
                        receiptItemMap.put(itemId, new ReceiptItemInfo(itemId, rOrderId, productId, sku, origQty));
                    }
                }
            }

            // 3. Load previously returned quantities for this purchase order's receipt items
            Map<Long, Integer> cumulativeReturnedMap = new HashMap<>();
            try (PreparedStatement returnedStmt = connection.prepareStatement(SELECT_EXISTING_RETURNED_FOR_ORDER_SQL)) {
                returnedStmt.setLong(1, orderId);
                try (ResultSet rs = returnedStmt.executeQuery()) {
                    while (rs.next()) {
                        cumulativeReturnedMap.put(rs.getLong("purchase_order_receipt_item_id"), rs.getInt("total_returned"));
                    }
                }
            }

            // 4. Pre-lock all involved products in strictly ascending order by ID to prevent deadlocks
            List<Long> productIdsToLock = sortedLines.stream()
                    .map(line -> receiptItemMap.get(line.purchaseOrderReceiptItemId()).productId())
                    .distinct()
                    .sorted()
                    .toList();

            Map<Long, Integer> currentStockMap = new HashMap<>();
            for (Long prodId : productIdsToLock) {
                try (PreparedStatement lockProdStmt = connection.prepareStatement(LOCK_PRODUCT_FOR_UPDATE_SQL)) {
                    lockProdStmt.setLong(1, prodId);
                    try (ResultSet rs = lockProdStmt.executeQuery()) {
                        if (!rs.next()) {
                            throw new ValidationException("Product not found with ID: " + prodId);
                        }
                        currentStockMap.put(prodId, rs.getInt("quantity"));
                    }
                }
            }

            // 5. Validate returnable quantities and available stock for all lines
            for (ReturnLineInput line : sortedLines) {
                ReceiptItemInfo info = receiptItemMap.get(line.purchaseOrderReceiptItemId());
                int previouslyReturned = cumulativeReturnedMap.getOrDefault(info.id(), 0);
                int returnable = Math.max(0, info.originalQty() - previouslyReturned);

                if (line.quantity() > returnable) {
                    throw new ValidationException(
                            "Return quantity (" + line.quantity() + ") exceeds returnable quantity (" +
                            returnable + ") for product: " + info.sku());
                }

                int currentStock = currentStockMap.getOrDefault(info.productId(), 0);
                if (currentStock < line.quantity()) {
                    throw new ValidationException(
                            "Insufficient stock to return product '" + info.sku() +
                            "'. Available in stock: " + currentStock + ", Requested return: " + line.quantity());
                }
            }

            // 5. Insert supplier return header
            long returnId;
            LocalDateTime returnDate;
            try (PreparedStatement insertReturnStmt = connection.prepareStatement(INSERT_RETURN_SQL, Statement.RETURN_GENERATED_KEYS)) {
                insertReturnStmt.setString(1, cleanReturnNumber);
                insertReturnStmt.setLong(2, orderId);
                insertReturnStmt.setString(3, (reason == null || reason.isBlank()) ? null : reason.trim());
                insertReturnStmt.executeUpdate();

                try (ResultSet rs = insertReturnStmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        returnId = rs.getLong(1);
                        returnDate = LocalDateTime.now();
                    } else {
                        throw new SQLException("Failed to retrieve generated key for supplier return.");
                    }
                }
            }

            SupplierReturn supplierReturn = new SupplierReturn(returnId, cleanReturnNumber, orderId, returnDate, reason, null);

            // 6. Process each return line: decrease stock, insert STOCK_OUT tx, insert return item
            for (ReturnLineInput line : lines) {
                ReceiptItemInfo info = receiptItemMap.get(line.purchaseOrderReceiptItemId());
                long productId = info.productId();
                int qty = line.quantity();

                // 6a. Decrease product stock
                try (PreparedStatement updateStockStmt = connection.prepareStatement(UPDATE_PRODUCT_STOCK_SQL)) {
                    updateStockStmt.setInt(1, qty);
                    updateStockStmt.setLong(2, productId);
                    updateStockStmt.setInt(3, qty);
                    int affected = updateStockStmt.executeUpdate();
                    if (affected != 1) {
                        throw new ValidationException("Insufficient stock to return product: " + info.sku());
                    }
                }

                // 6b. Insert STOCK_OUT inventory audit transaction
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

                // 6c. Insert supplier return item
                long returnItemId;
                try (PreparedStatement insertItemStmt = connection.prepareStatement(INSERT_RETURN_ITEM_SQL, Statement.RETURN_GENERATED_KEYS)) {
                    insertItemStmt.setLong(1, returnId);
                    insertItemStmt.setLong(2, info.id());
                    insertItemStmt.setLong(3, productId);
                    insertItemStmt.setInt(4, qty);
                    insertItemStmt.setString(5, line.reason());
                    insertItemStmt.setLong(6, txId);
                    insertItemStmt.executeUpdate();

                    try (ResultSet rs = insertItemStmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            returnItemId = rs.getLong(1);
                        } else {
                            throw new SQLException("Failed to retrieve generated key for return item.");
                        }
                    }
                }

                SupplierReturnItem returnItem = new SupplierReturnItem(
                        returnItemId, returnId, info.id(), productId, qty, line.reason(), txId);
                supplierReturn.addItem(returnItem);
            }

            connection.commit();
            return supplierReturn;

        } catch (ValidationException e) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                }
            }
            throw e;
        } catch (SQLException e) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                }
            }
            if ("23505".equals(e.getSQLState())) {
                throw new ValidationException("Supplier return already exists with return number: " + cleanReturnNumber);
            }
            throw new DatabaseException("Failed to record supplier return.", e);
        } finally {
            if (connection != null) {
                try {
                    connection.setAutoCommit(originalAutoCommit);
                } catch (SQLException ignored) {
                }
                try {
                    connection.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    @Override
    public List<SupplierReturn> findReturnsByOrderId(Long orderId) {
        if (orderId == null || orderId <= 0) {
            return Collections.emptyList();
        }

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement stmt = connection.prepareStatement(SELECT_RETURNS_BY_ORDER_ID_SQL)) {

            stmt.setLong(1, orderId);
            try (ResultSet rs = stmt.executeQuery()) {
                List<SupplierReturn> returns = new ArrayList<>();
                while (rs.next()) {
                    SupplierReturn r = mapReturnHeader(rs);
                    loadReturnItems(connection, r);
                    returns.add(r);
                }
                return Collections.unmodifiableList(returns);
            }

        } catch (SQLException e) {
            throw new DatabaseException("Failed to load returns for purchase order ID: " + orderId, e);
        }
    }

    @Override
    public Optional<SupplierReturn> findByReturnNumber(String returnNumber) {
        if (returnNumber == null || returnNumber.isBlank()) {
            return Optional.empty();
        }

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement stmt = connection.prepareStatement(SELECT_RETURN_BY_NUMBER_SQL)) {

            stmt.setString(1, returnNumber.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    SupplierReturn r = mapReturnHeader(rs);
                    loadReturnItems(connection, r);
                    return Optional.of(r);
                }
                return Optional.empty();
            }

        } catch (SQLException e) {
            throw new DatabaseException("Failed to find supplier return by return number: " + returnNumber, e);
        }
    }

    @Override
    public Optional<SupplierReturn> findById(Long id) {
        if (id == null || id <= 0) {
            return Optional.empty();
        }

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement stmt = connection.prepareStatement(SELECT_RETURN_BY_ID_SQL)) {

            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    SupplierReturn r = mapReturnHeader(rs);
                    loadReturnItems(connection, r);
                    return Optional.of(r);
                }
                return Optional.empty();
            }

        } catch (SQLException e) {
            throw new DatabaseException("Failed to find supplier return by ID: " + id, e);
        }
    }

    @Override
    public Map<Long, Integer> getReturnedQuantitiesForOrder(Long orderId) {
        Map<Long, Integer> map = new HashMap<>();
        if (orderId == null || orderId <= 0) {
            return map;
        }

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement stmt = connection.prepareStatement(SELECT_EXISTING_RETURNED_FOR_ORDER_SQL)) {

            stmt.setLong(1, orderId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getLong("purchase_order_receipt_item_id"), rs.getInt("total_returned"));
                }
                return map;
            }

        } catch (SQLException e) {
            throw new DatabaseException("Failed to query returned quantities for purchase order ID: " + orderId, e);
        }
    }

    @Override
    public Map<Long, Integer> getReturnedQuantitiesForReceiptItems(List<Long> receiptItemIds) {
        Map<Long, Integer> map = new HashMap<>();
        if (receiptItemIds == null || receiptItemIds.isEmpty()) {
            return map;
        }

        StringBuilder sql = new StringBuilder(
                "SELECT purchase_order_receipt_item_id, COALESCE(SUM(quantity), 0) AS total_returned " +
                "FROM supplier_return_items WHERE purchase_order_receipt_item_id IN (");
        for (int i = 0; i < receiptItemIds.size(); i++) {
            sql.append(i == 0 ? "?" : ", ?");
        }
        sql.append(") GROUP BY purchase_order_receipt_item_id");

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql.toString())) {

            for (int i = 0; i < receiptItemIds.size(); i++) {
                stmt.setLong(i + 1, receiptItemIds.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getLong("purchase_order_receipt_item_id"), rs.getInt("total_returned"));
                }
                return map;
            }

        } catch (SQLException e) {
            throw new DatabaseException("Failed to query returned quantities for receipt items.", e);
        }
    }

    @Override
    public boolean existsByReturnNumber(String returnNumber) {
        if (returnNumber == null || returnNumber.isBlank()) {
            return false;
        }

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement stmt = connection.prepareStatement(EXISTS_BY_RETURN_NUMBER_SQL)) {

            stmt.setString(1, returnNumber.trim());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }

        } catch (SQLException e) {
            throw new DatabaseException("Failed to check if supplier return exists by return number: " + returnNumber, e);
        }
    }

    @Override
    public boolean existsByProductId(Long productId) {
        if (productId == null || productId <= 0) {
            return false;
        }

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement stmt = connection.prepareStatement(EXISTS_BY_PRODUCT_ID_SQL)) {

            stmt.setLong(1, productId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }

        } catch (SQLException e) {
            throw new DatabaseException("Failed to check if supplier return references product ID: " + productId, e);
        }
    }

    private SupplierReturn mapReturnHeader(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        String returnNumber = rs.getString("return_number");
        long poId = rs.getLong("purchase_order_id");
        LocalDateTime returnDate = rs.getTimestamp("return_date").toLocalDateTime();
        String reason = rs.getString("reason");
        return new SupplierReturn(id, returnNumber, poId, returnDate, reason, null);
    }

    private void loadReturnItems(Connection connection, SupplierReturn r) throws SQLException {
        try (PreparedStatement itemStmt = connection.prepareStatement(SELECT_ITEMS_BY_RETURN_ID_SQL)) {
            itemStmt.setLong(1, r.getId());
            try (ResultSet rs = itemStmt.executeQuery()) {
                while (rs.next()) {
                    long itemId = rs.getLong("id");
                    long receiptItemId = rs.getLong("purchase_order_receipt_item_id");
                    long productId = rs.getLong("product_id");
                    int quantity = rs.getInt("quantity");
                    String reason = rs.getString("reason");
                    long txId = rs.getLong("inventory_transaction_id");

                    SupplierReturnItem item = new SupplierReturnItem(
                            itemId, r.getId(), receiptItemId, productId, quantity, reason, txId);
                    r.addItem(item);
                }
            }
        }
    }
}
