package com.stockflow.model;

/**
 * Supported product fields for sorting in queries and reports.
 */
public enum SortField {
    SKU,
    NAME,
    PRICE,
    QUANTITY;

    /**
     * Parses a string into a SortField (case-insensitive).
     *
     * @param value field name string
     * @return corresponding SortField
     * @throws IllegalArgumentException if value is null, blank, or unsupported
     */
    public static SortField fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Sort field cannot be blank.");
        }
        String normalized = value.trim().toUpperCase();
        for (SortField field : values()) {
            if (field.name().equals(normalized)) {
                return field;
            }
        }
        throw new IllegalArgumentException("Unsupported sort field: '" + value +
                "'. Supported fields: SKU, NAME, PRICE, QUANTITY.");
    }
}
