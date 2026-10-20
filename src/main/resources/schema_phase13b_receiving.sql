-- StockFlow Phase 13B: Purchase Order Receiving & Stock Integration
-- Tables for receiving deliveries against purchase orders, linked to inventory audit records.
-- Execution order:
-- 1. suppliers table (suppliers_schema.sql)
-- 2. products and inventory_transactions tables (schema.sql)
-- 3. products.supplier_id column (schema_phase12_connect_suppliers.sql)
-- 4. purchase_orders and purchase_order_items tables (schema_phase13a_purchase_orders.sql)
-- 5. Run this script:

CREATE TABLE IF NOT EXISTS purchase_order_receipts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    receipt_number VARCHAR(50) NOT NULL UNIQUE,
    purchase_order_id BIGINT NOT NULL REFERENCES purchase_orders(id) ON DELETE RESTRICT,
    receipt_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS purchase_order_receipt_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    receipt_id BIGINT NOT NULL REFERENCES purchase_order_receipts(id) ON DELETE RESTRICT,
    purchase_order_item_id BIGINT NOT NULL REFERENCES purchase_order_items(id) ON DELETE RESTRICT,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    inventory_transaction_id BIGINT NOT NULL REFERENCES inventory_transactions(id) ON DELETE RESTRICT
);
