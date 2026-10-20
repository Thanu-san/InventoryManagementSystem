package com.stockflow.repository;

import com.stockflow.database.DatabaseConnection;
import com.stockflow.exception.DatabaseException;
import com.stockflow.exception.ValidationException;
import com.stockflow.model.PurchaseOrder;
import com.stockflow.model.PurchaseOrderItem;
import com.stockflow.model.PurchaseOrderStatus;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PostgreSQL JDBC implementation of PurchaseOrderRepository.
 * Enforces transactional consistency and maps SQL errors cleanly.
 */
public class JdbcPurchaseOrderRepository implements PurchaseOrderRepository {

    private static final String INSERT_ORDER_SQL =
            "INSERT INTO purchase_orders (order_number, supplier_id, status, created_at) VALUES (?, ?, ?, ?)";

    private static final String INSERT_ITEM_SQL =
            "INSERT INTO purchase_order_items (purchase_order_id, product_id, product_sku, product_name, ordered_quantity, unit_cost) " +
            "VALUES (?, ?, ?, ?, ?, ?)";

    private static final String UPSERT_ITEM_SQL =
            "INSERT INTO purchase_order_items (purchase_order_id, product_id, product_sku, product_name, ordered_quantity, unit_cost) " +
            "VALUES (?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT (purchase_order_id, product_id) DO UPDATE SET " +
            "ordered_quantity = EXCLUDED.ordered_quantity, " +
            "unit_cost = EXCLUDED.unit_cost, " +
            "product_sku = EXCLUDED.product_sku, " +
            "product_name = EXCLUDED.product_name " +
            "RETURNING id";

    private static final String SELECT_ORDER_BY_ID_SQL =
            "SELECT id, order_number, supplier_id, status, created_at FROM purchase_orders WHERE id = ?";

    private static final String SELECT_ORDER_BY_NUMBER_SQL =
            "SELECT id, order_number, supplier_id, status, created_at FROM purchase_orders WHERE order_number = ?";

    private static final String EXISTS_BY_ORDER_NUMBER_SQL =
            "SELECT EXISTS (SELECT 1 FROM purchase_orders WHERE order_number = ?)";

    private static final String SELECT_ALL_ORDERS_SQL =
            "SELECT id, order_number, supplier_id, status, created_at FROM purchase_orders ORDER BY id DESC";

    private static final String SELECT_ORDERS_BY_STATUS_SQL =
            "SELECT id, order_number, supplier_id, status, created_at FROM purchase_orders WHERE status = ? ORDER BY id DESC";

    private static final String SELECT_ITEMS_BY_ORDER_ID_SQL =
            "SELECT poi.id, poi.purchase_order_id, poi.product_id, poi.product_sku, poi.product_name, poi.ordered_quantity, poi.unit_cost, " +
            "COALESCE(SUM(ri.quantity), 0) AS received_quantity " +
            "FROM purchase_order_items poi " +
            "LEFT JOIN purchase_order_receipt_items ri ON poi.id = ri.purchase_order_item_id " +
            "WHERE poi.purchase_order_id = ? " +
            "GROUP BY poi.id, poi.purchase_order_id, poi.product_id, poi.product_sku, poi.product_name, poi.ordered_quantity, poi.unit_cost " +
            "ORDER BY poi.id ASC";

    private static final String UPDATE_STATUS_SQL =
            "UPDATE purchase_orders SET status = ? WHERE id = ?";

    private static final String DELETE_ITEM_SQL =
            "DELETE FROM purchase_order_items WHERE purchase_order_id = ? AND product_id = ?";

    private static final String EXISTS_BY_SUPPLIER_ID_SQL =
            "SELECT EXISTS (SELECT 1 FROM purchase_orders WHERE supplier_id = ?)";

    private static final String EXISTS_BY_PRODUCT_ID_SQL =
            "SELECT EXISTS (SELECT 1 FROM purchase_order_items WHERE product_id = ?)";

    @Override
    public PurchaseOrder save(PurchaseOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("Purchase order cannot be null.");
        }
        if (order.getId() != null) {
            throw new IllegalArgumentException("Cannot save purchase order that already has an ID: " + order.getId());
        }

        Connection connection = null;
        try {
            connection = DatabaseConnection.getConnection();
            connection.setAutoCommit(false);

            try (PreparedStatement orderStmt = connection.prepareStatement(INSERT_ORDER_SQL, Statement.RETURN_GENERATED_KEYS)) {
                orderStmt.setString(1, order.getOrderNumber().trim());
                orderStmt.setLong(2, order.getSupplierId());
                orderStmt.setString(3, order.getStatus().name());
                orderStmt.setTimestamp(4, Timestamp.valueOf(order.getCreatedAt()));

                orderStmt.executeUpdate();

                try (ResultSet generatedKeys = orderStmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        order.setId(generatedKeys.getLong(1));
                    } else {
                        throw new DatabaseException("Failed to retrieve generated ID for purchase order.");
                    }
                }
            }

            // Save any initial line items
            if (!order.getItems().isEmpty()) {
                try (PreparedStatement itemStmt = connection.prepareStatement(INSERT_ITEM_SQL, Statement.RETURN_GENERATED_KEYS)) {
                    for (PurchaseOrderItem item : order.getItems()) {
                        itemStmt.setLong(1, order.getId());
                        itemStmt.setLong(2, item.getProductId());
                        itemStmt.setString(3, item.getProductSku());
                        itemStmt.setString(4, item.getProductName());
                        itemStmt.setInt(5, item.getOrderedQuantity());
                        itemStmt.setBigDecimal(6, item.getUnitCost());
                        itemStmt.executeUpdate();

                        try (ResultSet itemKeys = itemStmt.getGeneratedKeys()) {
                            if (itemKeys.next()) {
                                item.setId(itemKeys.getLong(1));
                                item.setPurchaseOrderId(order.getId());
                            }
                        }
                    }
                }
            }

            connection.commit();
            return order;
        } catch (SQLException e) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackEx) {
                    // Suppress or log rollback failure
                }
            }
            if ("23505".equals(e.getSQLState())) {
                throw new ValidationException("Purchase order already exists with order number: " + order.getOrderNumber(), e);
            }
            if ("23503".equals(e.getSQLState()) || "23001".equals(e.getSQLState())) {
                throw new ValidationException("Referenced supplier or product not found.", e);
            }
            throw new DatabaseException("Database error while saving purchase order: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                try {
                    connection.setAutoCommit(true);
                    connection.close();
                } catch (SQLException ignored) {
                }
            }
        }
    }

    @Override
    public Optional<PurchaseOrder> findById(Long id) {
        if (id == null || id <= 0) {
            return Optional.empty();
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ORDER_BY_ID_SQL)) {

            statement.setLong(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    PurchaseOrder order = mapRowToOrder(resultSet);
                    loadItemsForOrder(connection, order);
                    return Optional.of(order);
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Database error while finding purchase order by ID: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<PurchaseOrder> findByOrderNumber(String orderNumber) {
        if (orderNumber == null || orderNumber.isBlank()) {
            return Optional.empty();
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ORDER_BY_NUMBER_SQL)) {

            statement.setString(1, orderNumber.trim());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    PurchaseOrder order = mapRowToOrder(resultSet);
                    loadItemsForOrder(connection, order);
                    return Optional.of(order);
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Database error while finding purchase order by number: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean existsByOrderNumber(String orderNumber) {
        if (orderNumber == null || orderNumber.isBlank()) {
            return false;
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(EXISTS_BY_ORDER_NUMBER_SQL)) {

            statement.setString(1, orderNumber.trim());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getBoolean(1);
                }
                return false;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Database error while checking if purchase order exists: " + e.getMessage(), e);
        }
    }

    @Override
    public List<PurchaseOrder> findAll() {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL_ORDERS_SQL);
             ResultSet resultSet = statement.executeQuery()) {

            List<PurchaseOrder> orders = new ArrayList<>();
            while (resultSet.next()) {
                orders.add(mapRowToOrder(resultSet));
            }

            if (!orders.isEmpty()) {
                loadItemsForOrders(connection, orders);
            }
            return orders;
        } catch (SQLException e) {
            throw new DatabaseException("Database error while retrieving all purchase orders: " + e.getMessage(), e);
        }
    }

    @Override
    public List<PurchaseOrder> findByStatus(PurchaseOrderStatus status) {
        if (status == null) {
            return Collections.emptyList();
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ORDERS_BY_STATUS_SQL)) {

            statement.setString(1, status.name());

            try (ResultSet resultSet = statement.executeQuery()) {
                List<PurchaseOrder> orders = new ArrayList<>();
                while (resultSet.next()) {
                    orders.add(mapRowToOrder(resultSet));
                }

                if (!orders.isEmpty()) {
                    loadItemsForOrders(connection, orders);
                }
                return orders;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Database error while retrieving purchase orders by status: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean updateStatus(Long orderId, PurchaseOrderStatus status) {
        if (orderId == null || status == null) {
            return false;
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_STATUS_SQL)) {

            statement.setString(1, status.name());
            statement.setLong(2, orderId);

            int rowsAffected = statement.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Database error while updating purchase order status: " + e.getMessage(), e);
        }
    }

    @Override
    public PurchaseOrderItem saveOrUpdateItem(Long orderId, PurchaseOrderItem item) {
        if (orderId == null || item == null) {
            throw new IllegalArgumentException("Order ID and line item cannot be null.");
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPSERT_ITEM_SQL)) {

            statement.setLong(1, orderId);
            statement.setLong(2, item.getProductId());
            statement.setString(3, item.getProductSku().trim());
            statement.setString(4, item.getProductName().trim());
            statement.setInt(5, item.getOrderedQuantity());
            statement.setBigDecimal(6, item.getUnitCost());

            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    long generatedId = rs.getLong(1);
                    if (item.getId() == null) {
                        item.setId(generatedId);
                    }
                    if (item.getPurchaseOrderId() == null) {
                        item.setPurchaseOrderId(orderId);
                    }
                }
            }
            return item;
        } catch (SQLException e) {
            if ("23503".equals(e.getSQLState()) || "23001".equals(e.getSQLState())) {
                throw new ValidationException("Referenced order or product not found.", e);
            }
            throw new DatabaseException("Database error while saving or updating purchase order item: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean removeItem(Long orderId, Long productId) {
        if (orderId == null || productId == null) {
            return false;
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE_ITEM_SQL)) {

            statement.setLong(1, orderId);
            statement.setLong(2, productId);

            int rowsAffected = statement.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Database error while removing purchase order item: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean existsBySupplierId(Long supplierId) {
        if (supplierId == null || supplierId <= 0) {
            return false;
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(EXISTS_BY_SUPPLIER_ID_SQL)) {

            statement.setLong(1, supplierId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getBoolean(1);
                }
                return false;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Database error while checking if purchase orders exist for supplier ID: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean existsByProductId(Long productId) {
        if (productId == null || productId <= 0) {
            return false;
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(EXISTS_BY_PRODUCT_ID_SQL)) {

            statement.setLong(1, productId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getBoolean(1);
                }
                return false;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Database error while checking if purchase order items exist for product ID: " + e.getMessage(), e);
        }
    }

    // --- Private Helper Methods ---

    private PurchaseOrder mapRowToOrder(ResultSet rs) throws SQLException {
        Long id = rs.getLong("id");
        String orderNumber = rs.getString("order_number");
        Long supplierId = rs.getLong("supplier_id");
        PurchaseOrderStatus status = PurchaseOrderStatus.valueOf(rs.getString("status"));
        LocalDateTime createdAt = rs.getTimestamp("created_at").toLocalDateTime();

        return new PurchaseOrder(id, orderNumber, supplierId, status, createdAt, null);
    }

    private void loadItemsForOrder(Connection connection, PurchaseOrder order) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(SELECT_ITEMS_BY_ORDER_ID_SQL)) {
            stmt.setLong(1, order.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    order.addOrUpdateItem(mapRowToItem(rs));
                }
            }
        }
    }

    private void loadItemsForOrders(Connection connection, List<PurchaseOrder> orders) throws SQLException {
        if (orders.isEmpty()) return;

        Map<Long, PurchaseOrder> orderMap = new HashMap<>();
        StringBuilder inClause = new StringBuilder();
        for (int i = 0; i < orders.size(); i++) {
            orderMap.put(orders.get(i).getId(), orders.get(i));
            inClause.append("?");
            if (i < orders.size() - 1) {
                inClause.append(",");
            }
        }

        String sql = "SELECT poi.id, poi.purchase_order_id, poi.product_id, poi.product_sku, poi.product_name, poi.ordered_quantity, poi.unit_cost, " +
                     "COALESCE(SUM(ri.quantity), 0) AS received_quantity " +
                     "FROM purchase_order_items poi " +
                     "LEFT JOIN purchase_order_receipt_items ri ON poi.id = ri.purchase_order_item_id " +
                     "WHERE poi.purchase_order_id IN (" + inClause + ") " +
                     "GROUP BY poi.id, poi.purchase_order_id, poi.product_id, poi.product_sku, poi.product_name, poi.ordered_quantity, poi.unit_cost " +
                     "ORDER BY poi.id ASC";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            int paramIndex = 1;
            for (PurchaseOrder order : orders) {
                stmt.setLong(paramIndex++, order.getId());
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Long poId = rs.getLong("purchase_order_id");
                    PurchaseOrder po = orderMap.get(poId);
                    if (po != null) {
                        po.addOrUpdateItem(mapRowToItem(rs));
                    }
                }
            }
        }
    }

    private PurchaseOrderItem mapRowToItem(ResultSet rs) throws SQLException {
        int receivedQuantity = 0;
        try {
            receivedQuantity = rs.getInt("received_quantity");
        } catch (SQLException ignored) {
        }
        return new PurchaseOrderItem(
                rs.getLong("id"),
                rs.getLong("purchase_order_id"),
                rs.getLong("product_id"),
                rs.getString("product_sku"),
                rs.getString("product_name"),
                rs.getInt("ordered_quantity"),
                rs.getBigDecimal("unit_cost"),
                receivedQuantity
        );
    }
}
