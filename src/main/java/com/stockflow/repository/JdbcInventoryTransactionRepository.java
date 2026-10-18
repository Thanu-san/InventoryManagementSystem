package com.stockflow.repository;

import com.stockflow.database.DatabaseConnection;
import com.stockflow.exception.ValidationException;
import com.stockflow.model.InventoryTransaction;
import com.stockflow.model.TransactionType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * PostgreSQL JDBC implementation of InventoryTransactionRepository.
 * Manages atomic stock movements and transaction audit history using a single JDBC transaction.
 */
public class JdbcInventoryTransactionRepository implements InventoryTransactionRepository {

    private static final String SELECT_QTY_FOR_UPDATE_SQL =
            "SELECT quantity FROM products WHERE id = ? FOR UPDATE";

    private static final String SELECT_QTY_SQL =
            "SELECT quantity FROM products WHERE id = ?";

    private static final String STOCK_IN_UPDATE_SQL =
            "UPDATE products SET quantity = quantity + ? WHERE id = ?";

    private static final String STOCK_OUT_UPDATE_SQL =
            "UPDATE products SET quantity = quantity - ? WHERE id = ? AND quantity >= ?";

    private static final String INSERT_TRANSACTION_SQL =
            "INSERT INTO inventory_transactions (product_id, type, quantity) VALUES (?, ?, ?) RETURNING id, created_at";

    private static final String FIND_BY_PRODUCT_ID_SQL =
            "SELECT id, product_id, type, quantity, created_at FROM inventory_transactions WHERE product_id = ? ORDER BY created_at DESC, id DESC";

    private static final String FIND_ALL_SQL =
            "SELECT id, product_id, type, quantity, created_at FROM inventory_transactions ORDER BY created_at DESC, id DESC";

    @Override
    public InventoryTransaction recordMovement(Long productId, TransactionType type, int quantity) {
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("Product ID must be greater than zero.");
        }
        if (type == null) {
            throw new IllegalArgumentException("Transaction type cannot be null.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }

        Connection connection = null;
        boolean originalAutoCommit = true;

        try {
            connection = DatabaseConnection.getConnection();
            originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            // 1. Conditional Stock Update
            if (type == TransactionType.STOCK_IN) {
                // Lock row and check for integer overflow
                try (PreparedStatement checkStmt = connection.prepareStatement(SELECT_QTY_FOR_UPDATE_SQL)) {
                    checkStmt.setLong(1, productId);
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        if (!rs.next()) {
                            throw new ValidationException("Product not found with ID: " + productId);
                        }
                        long currentQty = rs.getInt("quantity");
                        if (currentQty + (long) quantity > Integer.MAX_VALUE) {
                            throw new ValidationException("Stock In would cause quantity to exceed maximum capacity.");
                        }
                    }
                }

                try (PreparedStatement updateStmt = connection.prepareStatement(STOCK_IN_UPDATE_SQL)) {
                    updateStmt.setInt(1, quantity);
                    updateStmt.setLong(2, productId);
                    int affected = updateStmt.executeUpdate();
                    if (affected != 1) {
                        throw new ValidationException("Product not found with ID: " + productId);
                    }
                }
            } else if (type == TransactionType.STOCK_OUT) {
                // Conditional update prevents negative stock and concurrent overselling
                try (PreparedStatement updateStmt = connection.prepareStatement(STOCK_OUT_UPDATE_SQL)) {
                    updateStmt.setInt(1, quantity);
                    updateStmt.setLong(2, productId);
                    updateStmt.setInt(3, quantity);
                    int affected = updateStmt.executeUpdate();

                    if (affected == 0) {
                        // Distinguish between nonexistent product and insufficient stock
                        try (PreparedStatement checkStmt = connection.prepareStatement(SELECT_QTY_SQL)) {
                            checkStmt.setLong(1, productId);
                            try (ResultSet rs = checkStmt.executeQuery()) {
                                if (rs.next()) {
                                    int available = rs.getInt("quantity");
                                    throw new ValidationException(
                                            "Insufficient stock. Available: " + available + ", Requested: " + quantity);
                                } else {
                                    throw new ValidationException("Product not found with ID: " + productId);
                                }
                            }
                        }
                    }
                }
            }

            // 2. Insert Transaction Record
            InventoryTransaction transaction;
            try (PreparedStatement insertStmt = connection.prepareStatement(INSERT_TRANSACTION_SQL)) {
                insertStmt.setLong(1, productId);
                insertStmt.setString(2, type.name());
                insertStmt.setInt(3, quantity);

                try (ResultSet rs = insertStmt.executeQuery()) {
                    if (rs.next()) {
                        long id = rs.getLong("id");
                        Timestamp ts = rs.getTimestamp("created_at");
                        LocalDateTime createdAt = ts.toLocalDateTime();
                        transaction = new InventoryTransaction(id, productId, type, quantity, createdAt);
                    } else {
                        throw new SQLException("Failed to retrieve generated transaction key.");
                    }
                }
            }

            // 3. Commit only after both operations succeed
            connection.commit();
            return transaction;

        } catch (SQLException e) {
            rollbackQuietly(connection, e);
            throw new RuntimeException("Database error during stock movement: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            rollbackQuietly(connection, e);
            throw e;
        } finally {
            closeQuietly(connection, originalAutoCommit);
        }
    }

    @Override
    public List<InventoryTransaction> findByProductId(Long productId) {
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("Product ID must be greater than zero.");
        }

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_PRODUCT_ID_SQL)) {

            statement.setLong(1, productId);

            try (ResultSet resultSet = statement.executeQuery()) {
                List<InventoryTransaction> transactions = new ArrayList<>();
                while (resultSet.next()) {
                    transactions.add(mapRowToTransaction(resultSet));
                }
                return transactions;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error while retrieving transactions for product: " + e.getMessage(), e);
        }
    }

    @Override
    public List<InventoryTransaction> findAll() {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_ALL_SQL);
             ResultSet resultSet = statement.executeQuery()) {

            List<InventoryTransaction> transactions = new ArrayList<>();
            while (resultSet.next()) {
                transactions.add(mapRowToTransaction(resultSet));
            }
            return transactions;
        } catch (SQLException e) {
            throw new RuntimeException("Database error while retrieving all transactions: " + e.getMessage(), e);
        }
    }

    private InventoryTransaction mapRowToTransaction(ResultSet rs) throws SQLException {
        return new InventoryTransaction(
                rs.getLong("id"),
                rs.getLong("product_id"),
                TransactionType.valueOf(rs.getString("type")),
                rs.getInt("quantity"),
                rs.getTimestamp("created_at").toLocalDateTime()
        );
    }

    private void rollbackQuietly(Connection connection, Exception mainException) {
        if (connection != null) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                mainException.addSuppressed(rollbackEx);
            }
        }
    }

    private void closeQuietly(Connection connection, boolean originalAutoCommit) {
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
