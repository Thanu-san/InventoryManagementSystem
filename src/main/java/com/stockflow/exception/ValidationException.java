package com.stockflow.exception;

/**
 * Thrown when business validation rules fail (e.g., negative price, blank SKU, duplicate SKU).
 */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
