-- StockFlow Phase 11: Supplier Management
-- Create the suppliers table for PostgreSQL.
-- Run this SQL manually against your PostgreSQL database before using supplier features.
-- This script does NOT drop or modify any existing tables.

CREATE TABLE IF NOT EXISTS suppliers (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    supplier_code   VARCHAR(50)  NOT NULL UNIQUE,
    name            VARCHAR(255) NOT NULL,
    contact_name    VARCHAR(255),
    phone           VARCHAR(50),
    email           VARCHAR(255),
    address         VARCHAR(500)
);
