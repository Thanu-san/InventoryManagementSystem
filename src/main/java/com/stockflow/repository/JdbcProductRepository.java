package com.stockflow.repository;

import com.stockflow.database.DatabaseConnection;
import com.stockflow.model.Product;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
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

    private static final String SELECT_BY_SKU_SQL =
            "SELECT id, sku, name, price, quantity, minimum_stock FROM products WHERE sku = ?";

    @Override
    public Optional<Product> findBySku(String sku) {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_SKU_SQL)) {

            statement.setString(1, sku);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    Product product = mapRowToProduct(resultSet);
                    return Optional.of(product);
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error while finding product by SKU: " + e.getMessage(), e);
        }
    }

    private Product mapRowToProduct(ResultSet rs) throws SQLException {
        return new Product(
                rs.getLong("id"),
                rs.getString("sku"),
                rs.getString("name"),
                rs.getBigDecimal("price"),
                rs.getInt("quantity"),
                rs.getInt("minimum_stock")
        );
    }

    private static final String EXISTS_BY_SKU_SQL =
            "SELECT EXISTS (SELECT 1 FROM products WHERE sku = ?)";

    @Override
    public boolean existsBySku(String sku) {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(EXISTS_BY_SKU_SQL)) {

            statement.setString(1, sku);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getBoolean(1);
                }
                return false;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error while checking if product exists by SKU: " + e.getMessage(), e);
        }
    }

    private static final String SELECT_ALL_SQL =
            "SELECT id, sku, name, price, quantity, minimum_stock FROM products ORDER BY id";

    @Override
    public List<Product> findAll() {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL_SQL);
             ResultSet resultSet = statement.executeQuery()) {

            List<Product> products = new ArrayList<>();
            while (resultSet.next()) {
                Product product = mapRowToProduct(resultSet);
                products.add(product);
            }
            return products;
        } catch (SQLException e) {
            throw new RuntimeException("Database error while retrieving all products: " + e.getMessage(), e);
        }
    }

    private static final String UPDATE_SQL =
            "UPDATE products SET name = ?, price = ?, minimum_stock = ? WHERE sku = ?";

    @Override
    public boolean update(Product product) {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_SQL)) {

            statement.setString(1, product.getName());
            statement.setBigDecimal(2, product.getPrice());
            statement.setInt(3, product.getMinimumStock());
            statement.setString(4, product.getSku());

            int rowsAffected = statement.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Database error while updating product: " + e.getMessage(), e);
        }
    }

    private static final String DELETE_BY_SKU_SQL =
            "DELETE FROM products WHERE sku = ?";

    @Override
    public boolean deleteBySku(String sku) {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE_BY_SKU_SQL)) {

            statement.setString(1, sku);

            int rowsAffected = statement.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Database error while deleting product by SKU: " + e.getMessage(), e);
        }
    }
}
