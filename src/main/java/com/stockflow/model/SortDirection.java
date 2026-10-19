package com.stockflow.model;

/**
 * Sort direction for queries: Ascending (ASC) or Descending (DESC).
 */
public enum SortDirection {
    ASC,
    DESC;

    /**
     * Parses a string into a SortDirection (case-insensitive).
     *
     * @param value direction string ("ASC", "DESC", "ASCENDING", "DESCENDING")
     * @return corresponding SortDirection
     * @throws IllegalArgumentException if value is null, blank, or unsupported
     */
    public static SortDirection fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Sort direction cannot be blank.");
        }
        String normalized = value.trim().toUpperCase();
        if ("ASC".equals(normalized) || "ASCENDING".equals(normalized)) {
            return ASC;
        }
        if ("DESC".equals(normalized) || "DESCENDING".equals(normalized)) {
            return DESC;
        }
        throw new IllegalArgumentException("Unsupported sort direction: '" + value +
                "'. Supported directions: ASC, DESC.");
    }
}
