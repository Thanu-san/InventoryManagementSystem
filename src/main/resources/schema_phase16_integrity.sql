-- ============================================================================
-- StockFlow Phase 16: Database Integrity & Performance Hardening
-- Incremental migration adding defensive check constraints, 1-to-1 audit uniqueness,
-- and missing foreign key indexes.
--
-- Safe to execute against existing databases without deleting or modifying existing data.
-- ============================================================================

-- 1. Defensive CHECK Constraints on Products
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_products_quantity_non_negative'
    ) THEN
        ALTER TABLE products
            ADD CONSTRAINT chk_products_quantity_non_negative CHECK (quantity >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_products_min_stock_non_negative'
    ) THEN
        ALTER TABLE products
            ADD CONSTRAINT chk_products_min_stock_non_negative CHECK (minimum_stock >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_products_price_non_negative'
    ) THEN
        ALTER TABLE products
            ADD CONSTRAINT chk_products_price_non_negative CHECK (price >= 0);
    END IF;
END $$;

-- 2. Audit Trail Integrity: 1-to-1 Mapping for Inbound/Outbound Inventory Transactions
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_po_receipt_item_tx'
    ) THEN
        ALTER TABLE purchase_order_receipt_items
            ADD CONSTRAINT uk_po_receipt_item_tx UNIQUE (inventory_transaction_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_supplier_return_item_tx'
    ) THEN
        ALTER TABLE supplier_return_items
            ADD CONSTRAINT uk_supplier_return_item_tx UNIQUE (inventory_transaction_id);
    END IF;
END $$;

-- 3. Foreign Key Performance Indexes (prevents sequential scans on FK cascade/restrict checks and joins)
CREATE INDEX IF NOT EXISTS idx_inventory_transactions_product_id
    ON inventory_transactions(product_id);

CREATE INDEX IF NOT EXISTS idx_products_supplier_id
    ON products(supplier_id);

CREATE INDEX IF NOT EXISTS idx_purchase_orders_supplier_id
    ON purchase_orders(supplier_id);

CREATE INDEX IF NOT EXISTS idx_purchase_orders_status
    ON purchase_orders(status);

CREATE INDEX IF NOT EXISTS idx_purchase_order_items_product_id
    ON purchase_order_items(product_id);

CREATE INDEX IF NOT EXISTS idx_purchase_order_receipts_po_id
    ON purchase_order_receipts(purchase_order_id);

CREATE INDEX IF NOT EXISTS idx_purchase_order_receipt_items_receipt_id
    ON purchase_order_receipt_items(receipt_id);

CREATE INDEX IF NOT EXISTS idx_purchase_order_receipt_items_product_id
    ON purchase_order_receipt_items(product_id);

CREATE INDEX IF NOT EXISTS idx_purchase_order_receipt_items_po_item_id
    ON purchase_order_receipt_items(purchase_order_item_id);

CREATE INDEX IF NOT EXISTS idx_supplier_returns_po_id
    ON supplier_returns(purchase_order_id);

CREATE INDEX IF NOT EXISTS idx_supplier_return_items_return_id
    ON supplier_return_items(supplier_return_id);

CREATE INDEX IF NOT EXISTS idx_supplier_return_items_receipt_item_id
    ON supplier_return_items(purchase_order_receipt_item_id);

CREATE INDEX IF NOT EXISTS idx_supplier_return_items_product_id
    ON supplier_return_items(product_id);
