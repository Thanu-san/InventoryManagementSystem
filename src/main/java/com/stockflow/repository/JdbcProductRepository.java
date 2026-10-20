package com.stockflow.repository;

import com.stockflow.database.DatabaseConnection;
import com.stockflow.exception.DatabaseException;
import com.stockflow.exception.ValidationException;
import com.stockflow.model.InventorySummary;
import com.stockflow.model.PagedResult;
import com.stockflow.model.Product;
import com.stockflow.model.SortDirection;
import com.stockflow.model.SortField;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL JDBC implementation of ProductRepository.
 * Handles database operations using raw JDBC and SQL queries.
 */
public class JdbcProductRepository implements ProductRepository {

    private static final String INSERT_SQL =
            "INSERT INTO products (sku, name, price, quantity, minimum_stock, supplier_id) VALUES (?, ?, ?, ?, ?, ?)";

    @Override
    public Product save(Product product) {
        if (product == null || product.getSku() == null || product.getSku().isBlank()) {
            throw new IllegalArgumentException("Product and its SKU cannot be null or blank.");
        }
        if (product.getId() != null) {
            throw new IllegalArgumentException("Cannot save a product that already has an ID: " + product.getId());
        }

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, product.getSku());
            statement.setString(2, product.getName());
            statement.setBigDecimal(3, product.getPrice());
            statement.setInt(4, product.getQuantity());
            statement.setInt(5, product.getMinimumStock());
            if (product.getSupplierId() != null) {
                statement.setLong(6, product.getSupplierId());
            } else {
                statement.setNull(6, java.sql.Types.BIGINT);
            }

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
            if ("23505".equals(e.getSQLState())) {
                throw new ValidationException("Product with SKU '" + product.getSku().trim() + "' already exists.", e);
            }
            if ("23503".equals(e.getSQLState())) {
                throw new ValidationException("Supplier not found with ID: " + product.getSupplierId(), e);
            }
            throw new DatabaseException("Database error while saving product: " + e.getMessage(), e);
        }
    }

    private static final String SELECT_BY_SKU_SQL =
            "SELECT id, sku, name, price, quantity, minimum_stock, supplier_id FROM products WHERE sku = ?";

    @Override
    public Optional<Product> findBySku(String sku) {
        if (sku == null || sku.isBlank()) {
            return Optional.empty();
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_SKU_SQL)) {

            statement.setString(1, sku.trim());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    Product product = mapRowToProduct(resultSet);
                    return Optional.of(product);
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Database error while finding product by SKU: " + e.getMessage(), e);
        }
    }

    private Product mapRowToProduct(ResultSet rs) throws SQLException {
        Long supplierId = rs.getObject("supplier_id") != null ? rs.getLong("supplier_id") : null;
        return new Product(
                rs.getLong("id"),
                rs.getString("sku"),
                rs.getString("name"),
                rs.getBigDecimal("price"),
                rs.getInt("quantity"),
                rs.getInt("minimum_stock"),
                supplierId
        );
    }

    private static final String EXISTS_BY_SKU_SQL =
            "SELECT EXISTS (SELECT 1 FROM products WHERE sku = ?)";

    @Override
    public boolean existsBySku(String sku) {
        if (sku == null || sku.isBlank()) {
            return false;
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(EXISTS_BY_SKU_SQL)) {

            statement.setString(1, sku.trim());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getBoolean(1);
                }
                return false;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Database error while checking if product exists by SKU: " + e.getMessage(), e);
        }
    }

    private static final String SELECT_ALL_SQL =
            "SELECT id, sku, name, price, quantity, minimum_stock, supplier_id FROM products ORDER BY id";

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
            throw new DatabaseException("Database error while retrieving all products: " + e.getMessage(), e);
        }
    }

    private static final String SELECT_LOW_STOCK_SQL =
            "SELECT id, sku, name, price, quantity, minimum_stock, supplier_id FROM products WHERE quantity <= minimum_stock ORDER BY quantity ASC, sku ASC";

    @Override
    public List<Product> findLowStockProducts() {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_LOW_STOCK_SQL);
             ResultSet resultSet = statement.executeQuery()) {

            List<Product> products = new ArrayList<>();
            while (resultSet.next()) {
                Product product = mapRowToProduct(resultSet);
                products.add(product);
            }
            return products;
        } catch (SQLException e) {
            throw new DatabaseException("Database error while retrieving low-stock products: " + e.getMessage(), e);
        }
    }

    private static final String SUMMARY_SQL =
            "SELECT " +
            "COUNT(*) AS total_products, " +
            "COALESCE(SUM(quantity), 0) AS total_units, " +
            "COUNT(CASE WHEN quantity <= minimum_stock THEN 1 END) AS low_stock_products, " +
            "COUNT(CASE WHEN quantity = 0 THEN 1 END) AS out_of_stock_products " +
            "FROM products";

    @Override
    public InventorySummary getInventorySummary() {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(SUMMARY_SQL);
             ResultSet resultSet = statement.executeQuery()) {

            if (resultSet.next()) {
                long totalProducts = resultSet.getLong("total_products");
                long totalUnits = resultSet.getLong("total_units");
                long lowStockProducts = resultSet.getLong("low_stock_products");
                long outOfStockProducts = resultSet.getLong("out_of_stock_products");

                return new InventorySummary(totalProducts, totalUnits, lowStockProducts, outOfStockProducts);
            }
            return new InventorySummary(0, 0, 0, 0);
        } catch (SQLException e) {
            throw new DatabaseException("Database error while retrieving inventory summary: " + e.getMessage(), e);
        }
    }

    private static final String SEARCH_BY_NAME_SQL =
            "SELECT id, sku, name, price, quantity, minimum_stock, supplier_id FROM products " +
            "WHERE name ILIKE ? ESCAPE '!' ORDER BY sku ASC";

    private String escapeLikePattern(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("!", "!!")
                   .replace("%", "!%")
                   .replace("_", "!_");
    }

    @Override
    public List<Product> searchByName(String nameQuery) {
        if (nameQuery == null || nameQuery.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String pattern = "%" + escapeLikePattern(nameQuery.trim()) + "%";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(SEARCH_BY_NAME_SQL)) {

            statement.setString(1, pattern);

            try (ResultSet resultSet = statement.executeQuery()) {
                List<Product> products = new ArrayList<>();
                while (resultSet.next()) {
                    products.add(mapRowToProduct(resultSet));
                }
                return products;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Database error while searching products by name: " + e.getMessage(), e);
        }
    }

    private static final String COUNT_SQL = "SELECT COUNT(*) FROM products";

    @Override
    public long count() {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(COUNT_SQL);
             ResultSet resultSet = statement.executeQuery()) {

            if (resultSet.next()) {
                return resultSet.getLong(1);
            }
            return 0L;
        } catch (SQLException e) {
            throw new DatabaseException("Database error while counting products: " + e.getMessage(), e);
        }
    }

    @Override
    public PagedResult<Product> findAll(int page, int size, SortField sortField, SortDirection sortDirection) {
        if (page < 1) {
            throw new IllegalArgumentException("Page number must be greater than or equal to 1.");
        }
        if (size < 1) {
            throw new IllegalArgumentException("Page size must be greater than or equal to 1.");
        }
        if (sortField == null) {
            throw new IllegalArgumentException("Sort field cannot be null.");
        }
        if (sortDirection == null) {
            throw new IllegalArgumentException("Sort direction cannot be null.");
        }

        long totalElements = count();
        if (totalElements == 0) {
            return new PagedResult<>(Collections.emptyList(), page, size, 0L);
        }

        long offset = (long) (page - 1) * size;
        if (offset >= totalElements) {
            return new PagedResult<>(Collections.emptyList(), page, size, totalElements);
        }

        String colName = switch (sortField) {
            case SKU -> "sku";
            case NAME -> "name";
            case PRICE -> "price";
            case QUANTITY -> "quantity";
        };
        String dirSql = sortDirection == SortDirection.DESC ? "DESC" : "ASC";

        String sql;
        if (sortField == SortField.SKU) {
            sql = "SELECT id, sku, name, price, quantity, minimum_stock, supplier_id FROM products " +
                  "ORDER BY sku " + dirSql + " LIMIT ? OFFSET ?";
        } else {
            sql = "SELECT id, sku, name, price, quantity, minimum_stock, supplier_id FROM products " +
                  "ORDER BY " + colName + " " + dirSql + ", sku ASC LIMIT ? OFFSET ?";
        }

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, size);
            statement.setLong(2, offset);

            try (ResultSet resultSet = statement.executeQuery()) {
                List<Product> products = new ArrayList<>(size);
                while (resultSet.next()) {
                    products.add(mapRowToProduct(resultSet));
                }
                return new PagedResult<>(products, page, size, totalElements);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Database error while retrieving paged products: " + e.getMessage(), e);
        }
    }

    private static final String UPDATE_SQL =
            "UPDATE products SET name = ?, price = ?, minimum_stock = ? WHERE sku = ?";

    @Override
    public boolean update(Product product) {
        if (product == null || product.getSku() == null || product.getSku().isBlank()) {
            return false;
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_SQL)) {

            statement.setString(1, product.getName());
            statement.setBigDecimal(2, product.getPrice());
            statement.setInt(3, product.getMinimumStock());
            statement.setString(4, product.getSku().trim());

            int rowsAffected = statement.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Database error while updating product: " + e.getMessage(), e);
        }
    }

    private static final String UPDATE_SUPPLIER_SQL =
            "UPDATE products SET supplier_id = ? WHERE sku = ?";

    @Override
    public boolean updateSupplier(String sku, Long supplierId) {
        if (sku == null || sku.isBlank()) {
            return false;
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_SUPPLIER_SQL)) {

            if (supplierId != null) {
                statement.setLong(1, supplierId);
            } else {
                statement.setNull(1, java.sql.Types.BIGINT);
            }
            statement.setString(2, sku.trim());

            int rowsAffected = statement.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            if ("23503".equals(e.getSQLState())) {
                throw new ValidationException("Supplier not found with ID: " + supplierId, e);
            }
            throw new DatabaseException("Database error while updating supplier for product: " + e.getMessage(), e);
        }
    }

    private static final String EXISTS_BY_SUPPLIER_ID_SQL =
            "SELECT EXISTS (SELECT 1 FROM products WHERE supplier_id = ?)";

    @Override
    public boolean existsBySupplierId(Long supplierId) {
        if (supplierId == null) {
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
            throw new DatabaseException("Database error while checking if products exist for supplier ID: " + e.getMessage(), e);
        }
    }

    private static final String DELETE_BY_SKU_SQL =
            "DELETE FROM products WHERE sku = ?";

    @Override
    public boolean deleteBySku(String sku) {
        if (sku == null || sku.isBlank()) {
            return false;
        }
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE_BY_SKU_SQL)) {

            statement.setString(1, sku.trim());

            int rowsAffected = statement.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            if ("23503".equals(e.getSQLState()) || "23001".equals(e.getSQLState())) {
                throw new ValidationException(
                        "Cannot delete product '" + sku.trim() + "' because it has associated transaction records.", e);
            }
            throw new DatabaseException("Database error while deleting product by SKU: " + e.getMessage(), e);
        }
    }
}
