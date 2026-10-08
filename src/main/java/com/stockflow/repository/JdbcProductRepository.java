package com.stockflow.repository;

import com.stockflow.database.DatabaseConnection;
import com.stockflow.model.Product;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL JDBC implementation of ProductRepository.
 * Handles database operations using raw JDBC and SQL queries.
 */
public class JdbcProductRepository implements ProductRepository {

    private static final String INSERT_SQL =
            "INSERT INTO products (sku, name, price, quantity, minimum_stock) VALUES (?, ?, ?, ?, ?)";

    @Override
    public Product save(Product product) {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, product.getSku());
            statement.setString(2, product.getName());
            statement.setBigDecimal(3, product.getPrice());
            statement.setInt(4, product.getQuantity());
            statement.setInt(5, product.getMinimumStock());

            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    long generatedId = generatedKeys.getLong(1);
                    product.setId(generatedId);
                } else {
                    throw new SQLException("Creating product failed, no generated ID returned.");
                }
            }

            return product;
        } catch (SQLException e) {
            throw new RuntimeException("Database error while saving product: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Product> findBySku(String sku) {
        throw new UnsupportedOperationException("findBySku() is not implemented yet in Phase 3.5");
    }

    @Override
    public boolean existsBySku(String sku) {
        throw new UnsupportedOperationException("existsBySku() is not implemented yet in Phase 3.5");
    }

    @Override
    public List<Product> findAll() {
        throw new UnsupportedOperationException("findAll() is not implemented yet in Phase 3.5");
    }

    @Override
    public boolean deleteBySku(String sku) {
        throw new UnsupportedOperationException("deleteBySku() is not implemented yet in Phase 3.5");
    }
}
