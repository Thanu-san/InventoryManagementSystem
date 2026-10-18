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
    price         NUMERIC(12, 2) NOT NULL,
    quantity      INT            NOT NULL DEFAULT 0,
    minimum_stock INT            NOT NULL DEFAULT 0
);

-- Table: inventory_transactions
-- Audit log of all stock movements (inbound and outbound) linked to products.
CREATE TABLE IF NOT EXISTS inventory_transactions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id),
    type VARCHAR(10) NOT NULL
        CHECK (type IN ('STOCK_IN', 'STOCK_OUT')),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
