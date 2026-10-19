package com.stockflow.repository;

import com.stockflow.database.DatabaseConnection;
import com.stockflow.exception.DatabaseException;
import com.stockflow.exception.ValidationException;
import com.stockflow.model.Supplier;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL JDBC implementation of SupplierRepository.
 * Handles database operations using raw JDBC and SQL queries.
 */
public class JdbcSupplierRepository implements SupplierRepository {

    private static final String INSERT_SQL =
            "INSERT INTO suppliers (supplier_code, name, contact_name, phone, email, address) VALUES (?, ?, ?, ?, ?, ?)";

    @Override
    public Supplier save(Supplier supplier) {
        if (supplier == null || supplier.getSupplierCode() == null || supplier.getSupplierCode().isBlank()) {
            throw new IllegalArgumentException("Supplier and its code cannot be null or blank.");
        }
        if (supplier.getId() != null) {
            throw new IllegalArgumentException("Cannot save a supplier that already has an ID: " + supplier.getId());
        }

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, supplier.getSupplierCode());
            statement.setString(2, supplier.getName());
            statement.setString(3, supplier.getContactName());
            statement.setString(4, supplier.getPhone());
            statement.setString(5, supplier.getEmail());
            statement.setString(6, supplier.getAddress());

            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    long generatedId = generatedKeys.getLong(1);
                    supplier.setId(generatedId);
                } else {
                    throw new SQLException("Creating supplier failed, no generated ID returned.");
                }
            }

            return supplier;
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                throw new ValidationException("Supplier with code '" + supplier.getSupplierCode().trim() + "' already exists.", e);
            }
            throw new DatabaseException("Database error while saving supplier: " + e.getMessage(), e);
        }
    }

    private static final String SELECT_BY_CODE_SQL =
            "SELECT id, supplier_code, name, contact_name, phone, email, address FROM suppliers WHERE supplier_code = ?";

    @Override
    public Optional<Supplier> findBySupplierCode(String supplierCode) {
        if (supplierCode == null || supplierCode.isBlank()) {
            return Optional.empty();
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_CODE_SQL)) {

            statement.setString(1, supplierCode.trim());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    Supplier supplier = mapRowToSupplier(resultSet);
                    return Optional.of(supplier);
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Database error while finding supplier by code: " + e.getMessage(), e);
        }
    }

    private Supplier mapRowToSupplier(ResultSet rs) throws SQLException {
        return new Supplier(
                rs.getLong("id"),
                rs.getString("supplier_code"),
                rs.getString("name"),
                rs.getString("contact_name"),
                rs.getString("phone"),
                rs.getString("email"),
                rs.getString("address")
        );
    }

    private static final String EXISTS_BY_CODE_SQL =
            "SELECT EXISTS (SELECT 1 FROM suppliers WHERE supplier_code = ?)";

    @Override
    public boolean existsBySupplierCode(String supplierCode) {
        if (supplierCode == null || supplierCode.isBlank()) {
            return false;
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(EXISTS_BY_CODE_SQL)) {

            statement.setString(1, supplierCode.trim());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getBoolean(1);
                }
                return false;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Database error while checking if supplier exists by code: " + e.getMessage(), e);
        }
    }

    private static final String SELECT_ALL_SQL =
            "SELECT id, supplier_code, name, contact_name, phone, email, address FROM suppliers ORDER BY id";

    @Override
    public List<Supplier> findAll() {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL_SQL);
             ResultSet resultSet = statement.executeQuery()) {

            List<Supplier> suppliers = new ArrayList<>();
            while (resultSet.next()) {
                Supplier supplier = mapRowToSupplier(resultSet);
                suppliers.add(supplier);
            }
            return suppliers;
        } catch (SQLException e) {
            throw new DatabaseException("Database error while retrieving all suppliers: " + e.getMessage(), e);
        }
    }

    private static final String UPDATE_SQL =
            "UPDATE suppliers SET name = ?, contact_name = ?, phone = ?, email = ?, address = ? WHERE supplier_code = ?";

    @Override
    public boolean update(Supplier supplier) {
        if (supplier == null || supplier.getSupplierCode() == null || supplier.getSupplierCode().isBlank()) {
            return false;
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_SQL)) {

            statement.setString(1, supplier.getName());
            statement.setString(2, supplier.getContactName());
            statement.setString(3, supplier.getPhone());
            statement.setString(4, supplier.getEmail());
            statement.setString(5, supplier.getAddress());
            statement.setString(6, supplier.getSupplierCode().trim());

            int rowsAffected = statement.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Database error while updating supplier: " + e.getMessage(), e);
        }
    }

    private static final String DELETE_BY_CODE_SQL =
            "DELETE FROM suppliers WHERE supplier_code = ?";

    @Override
    public boolean deleteBySupplierCode(String supplierCode) {
        if (supplierCode == null || supplierCode.isBlank()) {
            return false;
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE_BY_CODE_SQL)) {

            statement.setString(1, supplierCode.trim());

            int rowsAffected = statement.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Database error while deleting supplier by code: " + e.getMessage(), e);
        }
    }
}
