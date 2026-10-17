-- ============================================================================
-- StockFlow Database Schema
-- Database: stockflow_db
-- ============================================================================

-- Table: products
-- Stores inventory products with unique SKUs and protected invariant fields.
CREATE TABLE IF NOT EXISTS products (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku           VARCHAR(50)    NOT NULL UNIQUE,
    name          VARCHAR(255)   NOT NULL,
    price         NUMERIC(10, 2) NOT NULL,
    quantity      INT            NOT NULL DEFAULT 0,
    minimum_stock INT            NOT NULL DEFAULT 0
);

-- Index on SKU for fast lookup
CREATE INDEX IF NOT EXISTS idx_products_sku ON products (sku);
