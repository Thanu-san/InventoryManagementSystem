-- StockFlow Phase 12: Connect Suppliers to Products
-- Adds nullable supplier_id foreign key column to products table referencing suppliers(id).
-- Execution order:
-- 1. Ensure suppliers table exists (suppliers_schema.sql)
-- 2. Execute this script:

ALTER TABLE products 
ADD COLUMN IF NOT EXISTS supplier_id BIGINT REFERENCES suppliers(id) ON DELETE RESTRICT;
