package com.stockflow.model;

/**
 * Represents a supplier in the system.
 * Encapsulates domain state and protects its own internal integrity:
 * - Supplier code is immutable once created (serves as domain identity).
 * - ID can only be assigned once upon persistence.
 * - Name has a guarded setter preventing blank values.
 * - Contact fields are optional and nullable.
 */
public class Supplier {
    private Long id;
    private final String supplierCode;
    private String name;
    private String contactName;
    private String phone;
    private String email;
    private String address;

    // Constructor without ID (used when creating a new supplier before persistence)
    public Supplier(String supplierCode, String name, String contactName,
                    String phone, String email, String address) {
        this(null, supplierCode, name, contactName, phone, email, address);
    }

    // Full constructor (used when reconstituting an existing supplier, e.g. from storage)
    public Supplier(Long id, String supplierCode, String name, String contactName,
                    String phone, String email, String address) {
        if (supplierCode == null || supplierCode.isBlank()) {
            throw new IllegalArgumentException("Supplier code cannot be blank.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Supplier name cannot be blank.");
        }

        if (id != null) {
            setId(id);
        }
        this.supplierCode = supplierCode.trim();
        this.name = name.trim();
        this.contactName = normalizeOptional(contactName);
        this.phone = normalizeOptional(phone);
        this.email = normalizeOptional(email);
        this.address = normalizeOptional(address);
    }

    // --- ID: Can only be assigned once ---
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        if (this.id != null) {
            throw new IllegalStateException("Supplier ID is already assigned and cannot be changed.");
        }
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Supplier ID must be greater than zero.");
        }
        this.id = id;
    }

    // --- Supplier Code: Immutable identity ---
    public String getSupplierCode() {
        return supplierCode;
    }

    // --- Name: Guarded setter ---
    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Supplier name cannot be blank.");
        }
        this.name = name.trim();
    }

    // --- Optional contact fields ---
    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = normalizeOptional(contactName);
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = normalizeOptional(phone);
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = normalizeOptional(email);
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = normalizeOptional(address);
    }

    /**
     * Normalizes optional string fields: blank or null becomes null, otherwise trimmed.
     */
    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    @Override
    public String toString() {
        return "Supplier{" +
                "id=" + id +
                ", supplierCode='" + supplierCode + '\'' +
                ", name='" + name + '\'' +
                ", contactName='" + contactName + '\'' +
                ", phone='" + phone + '\'' +
                ", email='" + email + '\'' +
                ", address='" + address + '\'' +
                '}';
    }
}
