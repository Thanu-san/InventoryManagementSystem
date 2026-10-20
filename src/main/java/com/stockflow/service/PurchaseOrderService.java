package com.stockflow.service;

import com.stockflow.exception.ValidationException;
import com.stockflow.model.Product;
import com.stockflow.model.PurchaseOrder;
import com.stockflow.model.PurchaseOrderItem;
import com.stockflow.model.PurchaseOrderStatus;
import com.stockflow.model.Supplier;
import com.stockflow.repository.ProductRepository;
import com.stockflow.repository.PurchaseOrderRepository;
import com.stockflow.repository.SupplierRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service coordinating purchase order operations and business rules.
 * Handles draft creation, editing line items, placing, cancelling, and validation.
 * Ensures that product stock quantities are never mutated in this phase.
 */
public class PurchaseOrderService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final AtomicInteger SEQUENCE = new AtomicInteger(1001);

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;

    public PurchaseOrderService(PurchaseOrderRepository purchaseOrderRepository,
                                SupplierRepository supplierRepository,
                                ProductRepository productRepository) {
        if (purchaseOrderRepository == null) {
            throw new IllegalArgumentException("PurchaseOrderRepository cannot be null.");
        }
        if (supplierRepository == null) {
            throw new IllegalArgumentException("SupplierRepository cannot be null.");
        }
        if (productRepository == null) {
            throw new IllegalArgumentException("ProductRepository cannot be null.");
        }
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
    }

    /**
     * Creates a new draft purchase order for the given supplier with an auto-generated order number.
     */
    public PurchaseOrder createDraftOrder(String supplierCode) {
        String generatedOrderNumber = generateUniqueOrderNumber();
        return createDraftOrder(generatedOrderNumber, supplierCode);
    }

    /**
     * Creates a new draft purchase order with a specified order number for the given supplier.
     */
    public PurchaseOrder createDraftOrder(String orderNumber, String supplierCode) {
        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            throw new ValidationException("Order number cannot be blank.");
        }
        String cleanOrderNumber = orderNumber.trim();
        if (purchaseOrderRepository.existsByOrderNumber(cleanOrderNumber)) {
            throw new ValidationException("Purchase order already exists with order number: " + cleanOrderNumber);
        }

        if (supplierCode == null || supplierCode.trim().isEmpty()) {
            throw new ValidationException("Supplier code cannot be blank.");
        }
        String cleanSupplierCode = supplierCode.trim();

        Supplier supplier = supplierRepository.findBySupplierCode(cleanSupplierCode)
                .orElseThrow(() -> new ValidationException("Supplier not found with code: " + cleanSupplierCode));

        PurchaseOrder order = new PurchaseOrder(cleanOrderNumber, supplier.getId());
        return purchaseOrderRepository.save(order);
    }

    /**
     * Adds a new product line item or updates an existing line item on a draft purchase order.
     * Rejects modifications if the order is already placed or cancelled.
     */
    public PurchaseOrderItem addOrUpdateItem(String orderNumber, String productSku,
                                            int quantity, BigDecimal unitCost) {
        PurchaseOrder order = getExistingOrder(orderNumber);
        validateDraftStatus(order, "modify");

        if (productSku == null || productSku.trim().isEmpty()) {
            throw new ValidationException("Product SKU cannot be blank.");
        }
        String cleanSku = productSku.trim();

        Product product = productRepository.findBySku(cleanSku)
                .orElseThrow(() -> new ValidationException("Product not found with SKU: " + cleanSku));

        if (quantity <= 0) {
            throw new ValidationException("Ordered quantity must be greater than zero. Received: " + quantity);
        }
        if (unitCost == null || unitCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Unit cost must be greater than or equal to zero. Received: " + unitCost);
        }

        PurchaseOrderItem item = new PurchaseOrderItem(
                order.getId(),
                product.getId(),
                product.getSku(),
                product.getName(),
                quantity,
                unitCost
        );

        PurchaseOrderItem savedItem = purchaseOrderRepository.saveOrUpdateItem(order.getId(), item);
        order.addOrUpdateItem(savedItem);
        return savedItem;
    }

    /**
     * Removes a product line item from a draft purchase order.
     * Rejects modifications if the order is already placed or cancelled.
     */
    public boolean removeItem(String orderNumber, String productSku) {
        PurchaseOrder order = getExistingOrder(orderNumber);
        validateDraftStatus(order, "modify");

        if (productSku == null || productSku.trim().isEmpty()) {
            throw new ValidationException("Product SKU cannot be blank.");
        }
        String cleanSku = productSku.trim();

        Product product = productRepository.findBySku(cleanSku)
                .orElseThrow(() -> new ValidationException("Product not found with SKU: " + cleanSku));

        boolean removed = purchaseOrderRepository.removeItem(order.getId(), product.getId());
        if (!removed) {
            throw new ValidationException("Product '" + cleanSku + "' is not present on purchase order '" + order.getOrderNumber() + "'.");
        }
        order.removeItem(product.getId());
        return true;
    }

    /**
     * Retrieves a purchase order with all its line items by order number.
     */
    public Optional<PurchaseOrder> getOrderByNumber(String orderNumber) {
        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            throw new ValidationException("Order number cannot be blank.");
        }
        return purchaseOrderRepository.findByOrderNumber(orderNumber.trim());
    }

    /**
     * Returns all purchase orders.
     */
    public List<PurchaseOrder> getAllOrders() {
        return purchaseOrderRepository.findAll();
    }

    /**
     * Returns purchase orders filtered by lifecycle status.
     */
    public List<PurchaseOrder> getOrdersByStatus(PurchaseOrderStatus status) {
        if (status == null) {
            throw new ValidationException("Status filter cannot be null.");
        }
        return purchaseOrderRepository.findByStatus(status);
    }

    /**
     * Places a draft purchase order.
     * Validates that the order exists, is in DRAFT status, and contains at least one line item.
     */
    public PurchaseOrder placeOrder(String orderNumber) {
        PurchaseOrder order = getExistingOrder(orderNumber);

        if (order.getStatus() == PurchaseOrderStatus.PLACED) {
            throw new ValidationException("Purchase order '" + order.getOrderNumber() + "' is already placed.");
        }
        if (order.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new ValidationException("Cannot place purchase order '" + order.getOrderNumber() + "' because it has been cancelled.");
        }
        if (order.getItems().isEmpty()) {
            throw new ValidationException("Cannot place purchase order '" + order.getOrderNumber() + "' because it has no line items. Add at least one product line item first.");
        }

        boolean updated = purchaseOrderRepository.updateStatus(order.getId(), PurchaseOrderStatus.PLACED);
        if (!updated) {
            throw new ValidationException("Failed to update status for purchase order: " + order.getOrderNumber());
        }
        order.setStatus(PurchaseOrderStatus.PLACED);
        return order;
    }

    /**
     * Cancels a draft purchase order.
     * Rejects cancellation if the order is already placed.
     */
    public PurchaseOrder cancelOrder(String orderNumber) {
        PurchaseOrder order = getExistingOrder(orderNumber);

        if (order.getStatus() == PurchaseOrderStatus.PLACED) {
            throw new ValidationException("Cannot cancel purchase order '" + order.getOrderNumber() + "' because it is already placed.");
        }
        if (order.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new ValidationException("Purchase order '" + order.getOrderNumber() + "' is already cancelled.");
        }

        boolean updated = purchaseOrderRepository.updateStatus(order.getId(), PurchaseOrderStatus.CANCELLED);
        if (!updated) {
            throw new ValidationException("Failed to update status for purchase order: " + order.getOrderNumber());
        }
        order.setStatus(PurchaseOrderStatus.CANCELLED);
        return order;
    }

    /**
     * Resolves the supplier associated with the given purchase order.
     */
    public Optional<Supplier> getSupplierForOrder(PurchaseOrder order) {
        if (order == null || order.getSupplierId() == null) {
            return Optional.empty();
        }
        return supplierRepository.findById(order.getSupplierId());
    }

    // --- Private Helper Methods ---

    private PurchaseOrder getExistingOrder(String orderNumber) {
        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            throw new ValidationException("Order number cannot be blank.");
        }
        String clean = orderNumber.trim();
        return purchaseOrderRepository.findByOrderNumber(clean)
                .orElseThrow(() -> new ValidationException("Purchase order not found with order number: " + clean));
    }

    private void validateDraftStatus(PurchaseOrder order, String action) {
        if (order.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new ValidationException(
                    "Cannot " + action + " purchase order '" + order.getOrderNumber() + "' because its status is " +
                    order.getStatus() + ". Only DRAFT orders can be modified.");
        }
    }

    private synchronized String generateUniqueOrderNumber() {
        String datePrefix = LocalDateTime.now().format(DATE_FORMATTER);
        String candidate;
        do {
            candidate = "PO-" + datePrefix + "-" + SEQUENCE.getAndIncrement();
        } while (purchaseOrderRepository.existsByOrderNumber(candidate));
        return candidate;
    }
}
