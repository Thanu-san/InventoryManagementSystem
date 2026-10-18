package com.stockflow.repository;

import com.stockflow.exception.ValidationException;
import com.stockflow.model.InventoryTransaction;
import com.stockflow.model.Product;
import com.stockflow.model.TransactionType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * In-memory implementation of InventoryTransactionRepository.
 * Simulates atomic stock movements and transaction audit history for unit testing and learning.
 */
public class InMemoryInventoryTransactionRepository implements InventoryTransactionRepository {

    private final ProductRepository productRepository;
    private final List<InventoryTransaction> transactions = new ArrayList<>();
    private long currentId = 1L;

    public InMemoryInventoryTransactionRepository() {
        this(null);
    }

    public InMemoryInventoryTransactionRepository(ProductRepository productRepository) {
        if (productRepository != null && !(productRepository instanceof InMemoryProductRepository)) {
            throw new IllegalArgumentException(
                    "InMemoryInventoryTransactionRepository requires an InMemoryProductRepository, but received: "
                            + productRepository.getClass().getSimpleName());
        }
        this.productRepository = productRepository;
    }

    @Override
    public synchronized InventoryTransaction recordMovement(Long productId, TransactionType type, int quantity) {
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("Product ID must be greater than zero.");
        }
        if (type == null) {
            throw new IllegalArgumentException("Transaction type cannot be null.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }

        if (productRepository == null) {
            throw new IllegalStateException("ProductRepository is not configured for in-memory stock movements.");
        }

        Product product = productRepository.findAll().stream()
                .filter(p -> productId.equals(p.getId()))
                .findFirst()
                .orElseThrow(() -> new ValidationException("Product not found with ID: " + productId));

        int currentQty = product.getQuantity();
        int newQty;

        if (type == TransactionType.STOCK_IN) {
            if ((long) currentQty + quantity > Integer.MAX_VALUE) {
                throw new ValidationException("Stock In would cause quantity to exceed maximum capacity.");
            }
            newQty = currentQty + quantity;
        } else {
            if (currentQty < quantity) {
                throw new ValidationException(
                        "Insufficient stock. Available: " + currentQty + ", Requested: " + quantity);
            }
            newQty = currentQty - quantity;
        }

        ((InMemoryProductRepository) productRepository).updateQuantity(productId, newQty);

        InventoryTransaction tx = new InventoryTransaction(currentId++, productId, type, quantity, LocalDateTime.now());
        transactions.add(tx);
        return tx;
    }

    @Override
    public synchronized List<InventoryTransaction> findByProductId(Long productId) {
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("Product ID must be greater than zero.");
        }

        return transactions.stream()
                .filter(tx -> productId.equals(tx.getProductId()))
                .sorted(Comparator.comparing(InventoryTransaction::getCreatedAt).reversed()
                        .thenComparing(Comparator.comparing(InventoryTransaction::getId).reversed()))
                .collect(Collectors.toList());
    }

    @Override
    public synchronized List<InventoryTransaction> findAll() {
        return transactions.stream()
                .sorted(Comparator.comparing(InventoryTransaction::getCreatedAt).reversed()
                        .thenComparing(Comparator.comparing(InventoryTransaction::getId).reversed()))
                .collect(Collectors.toList());
    }
}
