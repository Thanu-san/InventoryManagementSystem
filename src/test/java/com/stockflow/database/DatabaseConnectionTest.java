package com.stockflow.database;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PostgreSQL Database Connection Tests")
class DatabaseConnectionTest {

    @Test
    @DisplayName("Should successfully connect to PostgreSQL stockflow_db")
    void shouldConnectToDatabase() throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            assertNotNull(connection, "Connection should not be null");
            assertFalse(connection.isClosed(), "Connection should be open");
            assertTrue(connection.isValid(2), "Connection should be valid within timeout");
            assertEquals("stockflow_db", connection.getCatalog(), "Should be connected to stockflow_db");
        }
    }
}
