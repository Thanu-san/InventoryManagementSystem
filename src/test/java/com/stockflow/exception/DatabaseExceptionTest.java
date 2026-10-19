package com.stockflow.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DatabaseException Tests")
class DatabaseExceptionTest {

    @Test
    @DisplayName("Should create DatabaseException with message")
    void shouldCreateDatabaseExceptionWithMessage() {
        DatabaseException exception = new DatabaseException("Database connection timed out");
        assertEquals("Database connection timed out", exception.getMessage());
        assertNull(exception.getCause());
        assertTrue(exception instanceof RuntimeException, "DatabaseException must be an unchecked RuntimeException");
    }

    @Test
    @DisplayName("Should create DatabaseException with message and cause")
    void shouldCreateDatabaseExceptionWithMessageAndCause() {
        SQLException sqlException = new SQLException("Connection refused", "08001");
        DatabaseException exception = new DatabaseException("Failed to connect", sqlException);

        assertEquals("Failed to connect", exception.getMessage());
        assertSame(sqlException, exception.getCause());
        assertEquals("08001", ((SQLException) exception.getCause()).getSQLState());
    }
}
