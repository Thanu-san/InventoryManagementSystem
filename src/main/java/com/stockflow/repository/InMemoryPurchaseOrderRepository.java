package com.stockflow.repository;

import com.stockflow.exception.ValidationException;
import com.stockflow.model.PurchaseOrder;
import com.stockflow.model.PurchaseOrderItem;
import com.stockflow.model.PurchaseOrderStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory thread-safe implementation of PurchaseOrderRepository for testing and offline development.
 */
public class InMemoryPurchaseOrderRepository implements PurchaseOrderRepository {

    private final Map<Long, PurchaseOrder> orders = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1);
    private final AtomicLong itemIdSequence = new AtomicLong(1);

    @Override
    public synchronized PurchaseOrder save(PurchaseOrder order) {
        if (order == null) {
            throw new IllegalArgumentException("Purchase order cannot be null.");
        }
        if (order.getId() != null) {
            throw new IllegalArgumentException("Cannot save purchase order that already has an ID: " + order.getId());
        }

        if (existsByOrderNumber(order.getOrderNumber())) {
            throw new ValidationException("Purchase order already exists with order number: " + order.getOrderNumber());
        }

        long generatedId = idSequence.getAndIncrement();
        order.setId(generatedId);

        // Assign IDs to any initial items if provided
        for (PurchaseOrderItem item : order.getItems()) {
            if (item.getId() == null) {
                item.setId(itemIdSequence.getAndIncrement());
            }
            if (item.getPurchaseOrderId() == null) {
                item.setPurchaseOrderId(generatedId);
            }
        }

        orders.put(generatedId, order);
        return order;
    }

    @Override
    public synchronized Optional<PurchaseOrder> findById(Long id) {
        if (id == null || id <= 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(orders.get(id));
    }

    @Override
    public synchronized Optional<PurchaseOrder> findByOrderNumber(String orderNumber) {
        if (orderNumber == null || orderNumber.isBlank()) {
            return Optional.empty();
        }
        String clean = orderNumber.trim();
        return orders.values().stream()
                .filter(o -> clean.equalsIgnoreCase(o.getOrderNumber()))
                .findFirst();
    }

    @Override
    public synchronized boolean existsByOrderNumber(String orderNumber) {
        return findByOrderNumber(orderNumber).isPresent();
    }

    @Override
    public synchronized List<PurchaseOrder> findAll() {
        return orders.values().stream()
                .sorted(Comparator.comparing(PurchaseOrder::getId).reversed())
                .toList();
    }

    @Override
    public synchronized List<PurchaseOrder> findByStatus(PurchaseOrderStatus status) {
        if (status == null) {
            return List.of();
        }
        return orders.values().stream()
                .filter(o -> status == o.getStatus())
                .sorted(Comparator.comparing(PurchaseOrder::getId).reversed())
                .toList();
    }

    @Override
    public synchronized boolean updateStatus(Long orderId, PurchaseOrderStatus status) {
        if (orderId == null || status == null) {
            return false;
        }
        PurchaseOrder order = orders.get(orderId);
        if (order == null) {
            return false;
        }
        order.setStatus(status);
        return true;
    }

    @Override
    public synchronized PurchaseOrderItem saveOrUpdateItem(Long orderId, PurchaseOrderItem item) {
        if (orderId == null || item == null) {
            throw new IllegalArgumentException("Order ID and item cannot be null.");
        }
        PurchaseOrder order = orders.get(orderId);
        if (order == null) {
            throw new IllegalArgumentException("Purchase order not found with ID: " + orderId);
        }

        Optional<PurchaseOrderItem> existing = order.findItemByProductId(item.getProductId());
        if (existing.isPresent()) {
            PurchaseOrderItem current = existing.get();
            current.setOrderedQuantity(item.getOrderedQuantity());
            current.setUnitCost(item.getUnitCost());
            return current;
        } else {
            if (item.getId() == null) {
                item.setId(itemIdSequence.getAndIncrement());
            }
            item.setPurchaseOrderId(orderId);
            order.addOrUpdateItem(item);
            return item;
        }
    }

    @Override
    public synchronized boolean removeItem(Long orderId, Long productId) {
        if (orderId == null || productId == null) {
            return false;
        }
        PurchaseOrder order = orders.get(orderId);
        if (order == null) {
            return false;
        }
        return order.removeItem(productId);
    }

    @Override
    public synchronized boolean existsBySupplierId(Long supplierId) {
        if (supplierId == null || supplierId <= 0) {
            return false;
        }
        return orders.values().stream()
                .anyMatch(o -> supplierId.equals(o.getSupplierId()));
    }

    @Override
    public synchronized boolean existsByProductId(Long productId) {
        if (productId == null || productId <= 0) {
            return false;
        }
        return orders.values().stream()
                .anyMatch(o -> o.findItemByProductId(productId).isPresent());
    }
}
