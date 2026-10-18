package com.stockflow.repository;

import com.stockflow.model.InventoryTransaction;
import com.stockflow.model.TransactionType;

import java.util.List;

/**
 * Defines data access and persistence operations for inventory transactions and atomic stock movements.
 */
public interface InventoryTransactionRepository {

    /**
     * Executes an atomic stock movement: updates the product quantity in storage and records the transaction.
     * Must execute both operations within a single atomic transaction.
     *
     * @param productId database ID of the product
     * @param type STOCK_IN or STOCK_OUT
     * @param quantity movement quantity (must be positive)
     * @return the created InventoryTransaction record
     */
    InventoryTransaction recordMovement(Long productId, TransactionType type, int quantity);

    /**
     * Retrieves transaction history for a product by its ID, ordered by createdAt DESC, id DESC.
     *
     * @param productId database ID of the product
     * @return list of transactions (empty list if none exist)
     */
    List<InventoryTransaction> findByProductId(Long productId);

    /**
     * Retrieves all transaction history, ordered by createdAt DESC, id DESC.
     *
     * @return list of all transactions (empty list if none exist)
     */
    List<InventoryTransaction> findAll();
}
