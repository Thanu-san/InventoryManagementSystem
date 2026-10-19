package com.stockflow.exception;

/**
 * Thrown when an unexpected database or infrastructure error occurs (e.g. connection failures, SQL execution errors).
 * Extends RuntimeException to distinguish database failures from business validation errors while preserving existing call sites.
 */
public class DatabaseException extends RuntimeException {

    public DatabaseException(String message) {
        super(message);
    }

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
