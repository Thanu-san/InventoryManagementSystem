package com.stockflow.repository;

import com.stockflow.exception.ValidationException;
import com.stockflow.model.PurchaseOrder;
import com.stockflow.model.PurchaseOrderItem;
import com.stockflow.model.PurchaseOrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InMemoryPurchaseOrderRepository Tests")
class InMemoryPurchaseOrderRepositoryTest {

    private InMemoryPurchaseOrderRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryPurchaseOrderRepository();
    }

    @Test
    @DisplayName("Should save purchase order and assign generated ID")
    void shouldSaveOrderAndAssignId() {
        PurchaseOrder order = new PurchaseOrder("PO-100", 1L);
        assertNull(order.getId());

        PurchaseOrder saved = repository.save(order);
        assertSame(order, saved);
        assertNotNull(saved.getId());
        assertTrue(saved.getId() > 0);
        assertEquals("PO-100", saved.getOrderNumber());
        assertEquals(1L, saved.getSupplierId());
        assertEquals(PurchaseOrderStatus.DRAFT, saved.getStatus());
    }

    @Test
    @DisplayName("Should reject saving order that already has an ID")
    void shouldRejectSavingOrderWithId() {
        PurchaseOrder order = new PurchaseOrder(99L, "PO-100", 1L, PurchaseOrderStatus.DRAFT, null, null);
        assertThrows(IllegalArgumentException.class, () -> repository.save(order));
    }

    @Test
    @DisplayName("Should reject saving order with duplicate order number")
    void shouldRejectDuplicateOrderNumber() {
        repository.save(new PurchaseOrder("PO-DUP", 1L));
        assertThrows(ValidationException.class, () -> repository.save(new PurchaseOrder("PO-DUP", 2L)));
    }

    @Test
    @DisplayName("Should find order by ID and by order number")
    void shouldFindOrderByIdAndOrderNumber() {
        PurchaseOrder saved = repository.save(new PurchaseOrder("PO-FIND", 2L));

        Optional<PurchaseOrder> byId = repository.findById(saved.getId());
        assertTrue(byId.isPresent());
        assertEquals("PO-FIND", byId.get().getOrderNumber());

        Optional<PurchaseOrder> byNumber = repository.findByOrderNumber("PO-FIND");
        assertTrue(byNumber.isPresent());
        assertEquals(saved.getId(), byNumber.get().getId());

        // Case-insensitive lookup
        assertTrue(repository.findByOrderNumber("po-find").isPresent());
        assertTrue(repository.findByOrderNumber("  PO-FIND  ").isPresent());

        // Nonexistent
        assertTrue(repository.findById(999L).isEmpty());
        assertTrue(repository.findByOrderNumber("NONEXIST").isEmpty());
    }

    @Test
    @DisplayName("Should check existence by order number")
    void shouldCheckExistenceByOrderNumber() {
        repository.save(new PurchaseOrder("PO-EXISTS", 1L));
        assertTrue(repository.existsByOrderNumber("PO-EXISTS"));
        assertTrue(repository.existsByOrderNumber("po-exists"));
        assertFalse(repository.existsByOrderNumber("PO-NOT-EXISTS"));
        assertFalse(repository.existsByOrderNumber(null));
        assertFalse(repository.existsByOrderNumber("   "));
    }

    @Test
    @DisplayName("Should add and update line items on an order")
    void shouldAddAndUpdateLineItems() {
        PurchaseOrder order = repository.save(new PurchaseOrder("PO-ITEMS", 1L));

        PurchaseOrderItem item1 = new PurchaseOrderItem(order.getId(), 10L, "SKU-1", "Product 1", 2, new BigDecimal("15.00"));
        PurchaseOrderItem savedItem1 = repository.saveOrUpdateItem(order.getId(), item1);

        assertNotNull(savedItem1.getId());
        assertEquals(order.getId(), savedItem1.getPurchaseOrderId());
        assertEquals(1, order.getItems().size());
        assertEquals(new BigDecimal("30.00"), order.getTotalAmount());

        // Update the item by submitting same productId with new quantity and cost
        PurchaseOrderItem updateItem = new PurchaseOrderItem(order.getId(), 10L, "SKU-1", "Product 1", 5, new BigDecimal("14.00"));
        PurchaseOrderItem updated = repository.saveOrUpdateItem(order.getId(), updateItem);

        assertEquals(1, order.getItems().size());
        assertEquals(5, updated.getOrderedQuantity());
        assertEquals(new BigDecimal("14.00"), updated.getUnitCost());
        assertEquals(new BigDecimal("70.00"), order.getTotalAmount());
    }

    @Test
    @DisplayName("Should remove line item from an order")
    void shouldRemoveLineItem() {
        PurchaseOrder order = repository.save(new PurchaseOrder("PO-REM", 1L));
        repository.saveOrUpdateItem(order.getId(), new PurchaseOrderItem(order.getId(), 10L, "SKU-1", "Prod 1", 2, new BigDecimal("10.00")));
        repository.saveOrUpdateItem(order.getId(), new PurchaseOrderItem(order.getId(), 20L, "SKU-2", "Prod 2", 3, new BigDecimal("5.00")));

        assertEquals(2, order.getItems().size());

        boolean removed = repository.removeItem(order.getId(), 10L);
        assertTrue(removed);
        assertEquals(1, order.getItems().size());
        assertFalse(order.findItemByProductId(10L).isPresent());
        assertTrue(order.findItemByProductId(20L).isPresent());

        assertFalse(repository.removeItem(order.getId(), 999L));
        assertFalse(repository.removeItem(999L, 20L));
    }

    @Test
    @DisplayName("Should update status and filter orders by status")
    void shouldUpdateStatusAndFilter() {
        PurchaseOrder po1 = repository.save(new PurchaseOrder("PO-1", 1L));
        PurchaseOrder po2 = repository.save(new PurchaseOrder("PO-2", 1L));
        PurchaseOrder po3 = repository.save(new PurchaseOrder("PO-3", 1L));

        repository.updateStatus(po2.getId(), PurchaseOrderStatus.PLACED);
        repository.updateStatus(po3.getId(), PurchaseOrderStatus.CANCELLED);

        List<PurchaseOrder> drafts = repository.findByStatus(PurchaseOrderStatus.DRAFT);
        assertEquals(1, drafts.size());
        assertEquals("PO-1", drafts.get(0).getOrderNumber());

        List<PurchaseOrder> placed = repository.findByStatus(PurchaseOrderStatus.PLACED);
        assertEquals(1, placed.size());
        assertEquals("PO-2", placed.get(0).getOrderNumber());

        List<PurchaseOrder> cancelled = repository.findByStatus(PurchaseOrderStatus.CANCELLED);
        assertEquals(1, cancelled.size());
        assertEquals("PO-3", cancelled.get(0).getOrderNumber());

        List<PurchaseOrder> all = repository.findAll();
        assertEquals(3, all.size());
    }

    @Test
    @DisplayName("Should verify existence by supplier ID and product ID")
    void shouldVerifyExistenceBySupplierAndProductId() {
        PurchaseOrder order = repository.save(new PurchaseOrder("PO-REF", 100L));
        repository.saveOrUpdateItem(order.getId(), new PurchaseOrderItem(order.getId(), 200L, "SKU-REF", "Ref Prod", 1, new BigDecimal("10.00")));

        assertTrue(repository.existsBySupplierId(100L));
        assertFalse(repository.existsBySupplierId(999L));

        assertTrue(repository.existsByProductId(200L));
        assertFalse(repository.existsByProductId(999L));
    }
}
