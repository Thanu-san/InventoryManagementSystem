package com.stockflow.repository;

import com.stockflow.exception.ValidationException;
import com.stockflow.model.InventoryTransaction;
import com.stockflow.model.Product;
import com.stockflow.model.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InMemoryInventoryTransactionRepository Unit Tests")
class InMemoryInventoryTransactionRepositoryTest {

    private InMemoryProductRepository productRepository;
    private InMemoryInventoryTransactionRepository transactionRepository;
    private Product product;

    @BeforeEach
    void setUp() {
        productRepository = new InMemoryProductRepository();
        transactionRepository = new InMemoryInventoryTransactionRepository(productRepository);
        product = productRepository.save(new Product("SKU-100", "Widget", new BigDecimal("19.99"), 50, 10));
    }

    @Test
    @DisplayName("Should successfully record STOCK_IN and increment product quantity")
    void shouldRecordStockInAndIncrementQuantity() {
        InventoryTransaction tx = transactionRepository.recordMovement(product.getId(), TransactionType.STOCK_IN, 20);

        assertNotNull(tx.getId());
        assertEquals(product.getId(), tx.getProductId());
        assertEquals(TransactionType.STOCK_IN, tx.getType());
        assertEquals(20, tx.getQuantity());
        assertNotNull(tx.getCreatedAt());

        Product updated = productRepository.findBySku("SKU-100").orElseThrow();
        assertEquals(70, updated.getQuantity());
    }

    @Test
    @DisplayName("Should successfully record STOCK_OUT and decrement product quantity")
    void shouldRecordStockOutAndDecrementQuantity() {
        InventoryTransaction tx = transactionRepository.recordMovement(product.getId(), TransactionType.STOCK_OUT, 30);

        assertNotNull(tx.getId());
        assertEquals(product.getId(), tx.getProductId());
        assertEquals(TransactionType.STOCK_OUT, tx.getType());
        assertEquals(30, tx.getQuantity());

        Product updated = productRepository.findBySku("SKU-100").orElseThrow();
        assertEquals(20, updated.getQuantity());
    }

    @Test
    @DisplayName("Should allow STOCK_OUT reducing quantity to exactly zero")
    void shouldAllowStockOutToZero() {
        InventoryTransaction tx = transactionRepository.recordMovement(product.getId(), TransactionType.STOCK_OUT, 50);

        assertNotNull(tx);
        Product updated = productRepository.findBySku("SKU-100").orElseThrow();
        assertEquals(0, updated.getQuantity());
    }

    @Test
    @DisplayName("Should reject STOCK_OUT when quantity exceeds available stock")
    void shouldRejectStockOutExceedingStock() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                transactionRepository.recordMovement(product.getId(), TransactionType.STOCK_OUT, 51));

        assertTrue(ex.getMessage().contains("Insufficient stock"));
        Product unchanged = productRepository.findBySku("SKU-100").orElseThrow();
        assertEquals(50, unchanged.getQuantity());
    }

    @Test
    @DisplayName("Should reject STOCK_IN when quantity causes integer overflow")
    void shouldRejectStockInOverflow() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                transactionRepository.recordMovement(product.getId(), TransactionType.STOCK_IN, Integer.MAX_VALUE));

        assertTrue(ex.getMessage().contains("maximum capacity"));
    }

    @Test
    @DisplayName("Should reject movement for nonexistent product ID")
    void shouldRejectNonexistentProduct() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                transactionRepository.recordMovement(9999L, TransactionType.STOCK_IN, 10));

        assertTrue(ex.getMessage().contains("Product not found with ID: 9999"));
    }

    @Test
    @DisplayName("Should validate parameters on recordMovement")
    void shouldValidateParameters() {
        assertThrows(IllegalArgumentException.class, () ->
                transactionRepository.recordMovement(null, TransactionType.STOCK_IN, 10));
        assertThrows(IllegalArgumentException.class, () ->
                transactionRepository.recordMovement(0L, TransactionType.STOCK_IN, 10));
        assertThrows(IllegalArgumentException.class, () ->
                transactionRepository.recordMovement(product.getId(), null, 10));
        assertThrows(IllegalArgumentException.class, () ->
                transactionRepository.recordMovement(product.getId(), TransactionType.STOCK_IN, 0));
        assertThrows(IllegalArgumentException.class, () ->
                transactionRepository.recordMovement(product.getId(), TransactionType.STOCK_IN, -5));
    }

    @Test
    @DisplayName("Should return transactions ordered by createdAt DESC, id DESC")
    void shouldReturnTransactionsInDescendingOrder() {
        transactionRepository.recordMovement(product.getId(), TransactionType.STOCK_IN, 10);
        transactionRepository.recordMovement(product.getId(), TransactionType.STOCK_OUT, 5);
        transactionRepository.recordMovement(product.getId(), TransactionType.STOCK_IN, 15);

        List<InventoryTransaction> list = transactionRepository.findByProductId(product.getId());
        assertEquals(3, list.size());
        assertEquals(15, list.get(0).getQuantity());
        assertEquals(5, list.get(1).getQuantity());
        assertEquals(10, list.get(2).getQuantity());

        assertTrue(list.get(0).getId() > list.get(1).getId());
        assertTrue(list.get(1).getId() > list.get(2).getId());
    }

    @Test
    @DisplayName("Should filter transactions by product ID")
    void shouldFilterByProductId() {
        Product product2 = productRepository.save(new Product("SKU-200", "Gadget", new BigDecimal("9.99"), 10, 2));

        transactionRepository.recordMovement(product.getId(), TransactionType.STOCK_IN, 5);
        transactionRepository.recordMovement(product2.getId(), TransactionType.STOCK_IN, 8);

        List<InventoryTransaction> p1List = transactionRepository.findByProductId(product.getId());
        assertEquals(1, p1List.size());
        assertEquals(5, p1List.get(0).getQuantity());

        List<InventoryTransaction> p2List = transactionRepository.findByProductId(product2.getId());
        assertEquals(1, p2List.size());
        assertEquals(8, p2List.get(0).getQuantity());

        List<InventoryTransaction> all = transactionRepository.findAll();
        assertEquals(2, all.size());
    }
}
