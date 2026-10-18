package com.stockflow.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InventoryTransaction Model Tests")
class InventoryTransactionTest {

    @Test
    @DisplayName("Should create transaction without ID for new movement")
    void shouldCreateTransactionWithoutId() {
        InventoryTransaction tx = new InventoryTransaction(10L, TransactionType.STOCK_IN, 25);

        assertNull(tx.getId(), "ID should be null before persistence");
        assertEquals(10L, tx.getProductId());
        assertEquals(TransactionType.STOCK_IN, tx.getType());
        assertEquals(25, tx.getQuantity());
        assertNotNull(tx.getCreatedAt());
    }

    @Test
    @DisplayName("Should create transaction with full arguments for reconstitution")
    void shouldCreateTransactionWithFullArguments() {
        LocalDateTime timestamp = LocalDateTime.of(2026, 10, 18, 12, 0);
        InventoryTransaction tx = new InventoryTransaction(100L, 5L, TransactionType.STOCK_OUT, 12, timestamp);

        assertEquals(100L, tx.getId());
        assertEquals(5L, tx.getProductId());
        assertEquals(TransactionType.STOCK_OUT, tx.getType());
        assertEquals(12, tx.getQuantity());
        assertEquals(timestamp, tx.getCreatedAt());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when product ID is null or non-positive")
    void shouldFailWhenProductIdIsInvalid() {
        assertThrows(IllegalArgumentException.class, () ->
                new InventoryTransaction(null, TransactionType.STOCK_IN, 10));
        assertThrows(IllegalArgumentException.class, () ->
                new InventoryTransaction(0L, TransactionType.STOCK_IN, 10));
        assertThrows(IllegalArgumentException.class, () ->
                new InventoryTransaction(-5L, TransactionType.STOCK_IN, 10));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when transaction type is null")
    void shouldFailWhenTypeIsNull() {
        assertThrows(IllegalArgumentException.class, () ->
                new InventoryTransaction(1L, null, 10));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when quantity is zero or negative")
    void shouldFailWhenQuantityIsZeroOrNegative() {
        assertThrows(IllegalArgumentException.class, () ->
                new InventoryTransaction(1L, TransactionType.STOCK_IN, 0));
        assertThrows(IllegalArgumentException.class, () ->
                new InventoryTransaction(1L, TransactionType.STOCK_OUT, -5));
    }

    @Test
    @DisplayName("Should successfully assign ID once and reject subsequent reassignment")
    void shouldAssignIdOnceAndRejectReassignment() {
        InventoryTransaction tx = new InventoryTransaction(1L, TransactionType.STOCK_IN, 5);
        assertNull(tx.getId());

        tx.setId(42L);
        assertEquals(42L, tx.getId());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> tx.setId(99L));
        assertTrue(ex.getMessage().contains("already assigned"));
    }

    @Test
    @DisplayName("Should reject invalid ID assignments")
    void shouldRejectInvalidIdAssignment() {
        InventoryTransaction tx = new InventoryTransaction(1L, TransactionType.STOCK_IN, 5);

        assertThrows(IllegalArgumentException.class, () -> tx.setId(null));
        assertThrows(IllegalArgumentException.class, () -> tx.setId(0L));
        assertThrows(IllegalArgumentException.class, () -> tx.setId(-10L));
    }

    @Test
    @DisplayName("Should produce meaningful toString output")
    void shouldProduceMeaningfulToString() {
        InventoryTransaction tx = new InventoryTransaction(1L, 2L, TransactionType.STOCK_IN, 10, LocalDateTime.now());
        String str = tx.toString();

        assertTrue(str.contains("id=1"));
        assertTrue(str.contains("productId=2"));
        assertTrue(str.contains("STOCK_IN"));
        assertTrue(str.contains("quantity=10"));
    }
}
