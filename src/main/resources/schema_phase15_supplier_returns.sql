-- StockFlow Phase 15: Supplier Returns & Damaged Goods
-- Creates tables for supplier returns and line items linked to receipt items and inventory audit transactions.
-- Execution order:
-- 1. suppliers table (suppliers_schema.sql)
-- 2. products and inventory_transactions tables (schema.sql)
-- 3. products.supplier_id column (schema_phase12_connect_suppliers.sql)
-- 4. purchase_orders and purchase_order_items tables (schema_phase13a_purchase_orders.sql)
-- 5. purchase_order_receipts and purchase_order_receipt_items tables (schema_phase13b_receiving.sql)
-- 6. Run this script:

CREATE TABLE IF NOT EXISTS supplier_returns (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    return_number VARCHAR(50) NOT NULL UNIQUE,
    purchase_order_id BIGINT NOT NULL REFERENCES purchase_orders(id) ON DELETE RESTRICT,
    return_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS supplier_return_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    supplier_return_id BIGINT NOT NULL REFERENCES supplier_returns(id) ON DELETE RESTRICT,
    purchase_order_receipt_item_id BIGINT NOT NULL REFERENCES purchase_order_receipt_items(id) ON DELETE RESTRICT,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    reason VARCHAR(500),
    inventory_transaction_id BIGINT NOT NULL REFERENCES inventory_transactions(id) ON DELETE RESTRICT
);
