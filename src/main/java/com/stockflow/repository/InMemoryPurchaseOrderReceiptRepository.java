package com.stockflow.repository;

import com.stockflow.exception.ValidationException;
import com.stockflow.model.InventoryTransaction;
import com.stockflow.model.Product;
import com.stockflow.model.PurchaseOrder;
import com.stockflow.model.PurchaseOrderItem;
import com.stockflow.model.PurchaseOrderReceipt;
import com.stockflow.model.PurchaseOrderReceiptItem;
import com.stockflow.model.PurchaseOrderStatus;
import com.stockflow.model.ReceiptLineInput;
import com.stockflow.model.TransactionType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory thread-safe implementation of PurchaseOrderReceiptRepository.
 * Coordinates receipt tracking, product stock increases, and inventory transaction recording.
 */
public class InMemoryPurchaseOrderReceiptRepository implements PurchaseOrderReceiptRepository {

    private final PurchaseOrderRepository poRepository;
    private final ProductRepository productRepository;
    private final InventoryTransactionRepository transactionRepository;

    private final Map<Long, PurchaseOrderReceipt> receipts = new ConcurrentHashMap<>();
    private final AtomicLong receiptIdSequence = new AtomicLong(1);
    private final AtomicLong receiptItemIdSequence = new AtomicLong(1);

    public InMemoryPurchaseOrderReceiptRepository(PurchaseOrderRepository poRepository,
                                                ProductRepository productRepository,
                                                InventoryTransactionRepository transactionRepository) {
        if (poRepository == null) {
            throw new IllegalArgumentException("PurchaseOrderRepository cannot be null.");
        }
        if (productRepository == null) {
            throw new IllegalArgumentException("ProductRepository cannot be null.");
        }
        if (transactionRepository == null) {
            throw new IllegalArgumentException("InventoryTransactionRepository cannot be null.");
        }
        this.poRepository = poRepository;
        this.productRepository = productRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public synchronized PurchaseOrderReceipt recordReceipt(Long orderId, String receiptNumber,
                                                          String notes, List<ReceiptLineInput> lines) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("Purchase order ID must be greater than zero.");
        }
        if (receiptNumber == null || receiptNumber.isBlank()) {
            throw new ValidationException("Receipt number cannot be blank.");
        }
        String cleanReceiptNumber = receiptNumber.trim();
        if (findByReceiptNumber(cleanReceiptNumber).isPresent()) {
            throw new ValidationException("Receipt already exists with receipt number: " + cleanReceiptNumber);
        }
        if (lines == null || lines.isEmpty()) {
            throw new ValidationException("Cannot record receipt with no received items.");
        }

        PurchaseOrder order = poRepository.findById(orderId)
                .orElseThrow(() -> new ValidationException("Purchase order not found with ID: " + orderId));

        if (!order.isReceivable()) {
            throw new ValidationException(
                    "Cannot receive goods against purchase order '" + order.getOrderNumber() +
                    "' because its status is " + order.getStatus() + ". Only PLACED or PARTIALLY_RECEIVED orders can be received.");
        }

        // Validate duplicates and remaining quantities
        Set<Long> seenItemIds = new HashSet<>();
        for (ReceiptLineInput line : lines) {
            if (line == null) {
                throw new ValidationException("Receipt line cannot be null.");
            }
            if (!seenItemIds.add(line.purchaseOrderItemId())) {
                throw new ValidationException("Duplicate receipt line for item ID: " + line.purchaseOrderItemId());
            }

            PurchaseOrderItem orderItem = order.findItemById(line.purchaseOrderItemId())
                    .orElseThrow(() -> new ValidationException("Purchase order item not found with ID: " + line.purchaseOrderItemId()));

            if (line.quantity() <= 0) {
                throw new ValidationException("Received quantity must be greater than zero for product: " + orderItem.getProductSku());
            }

            int remaining = orderItem.getRemainingQuantity();
            if (line.quantity() > remaining) {
                throw new ValidationException(
                        "Received quantity (" + line.quantity() + ") exceeds remaining quantity (" +
                        remaining + ") for product: " + orderItem.getProductSku());
            }
        }

        // Create receipt
        PurchaseOrderReceipt receipt = new PurchaseOrderReceipt(cleanReceiptNumber, orderId, notes);
        receipt.setId(receiptIdSequence.getAndIncrement());

        // Process each line item: stock movement, inventory transaction, receipt item
        for (ReceiptLineInput line : lines) {
            PurchaseOrderItem orderItem = order.findItemById(line.purchaseOrderItemId()).orElseThrow();
            Product product = productRepository.findAll().stream()
                    .filter(p -> orderItem.getProductId().equals(p.getId()))
                    .findFirst()
                    .orElseThrow(() -> new ValidationException("Product not found with ID: " + orderItem.getProductId()));

            // 1. Record stock movement and transaction
            InventoryTransaction tx = transactionRepository.recordMovement(
                    product.getId(), TransactionType.STOCK_IN, line.quantity());

            // 2. Create receipt item
            PurchaseOrderReceiptItem receiptItem = new PurchaseOrderReceiptItem(
                    receiptItemIdSequence.getAndIncrement(),
                    receipt.getId(),
                    orderItem.getId(),
                    product.getId(),
                    line.quantity(),
                    tx.getId()
            );
            receipt.addItem(receiptItem);

            // 3. Update order item cumulative received quantity
            orderItem.addReceivedQuantity(line.quantity());
        }

        // 4. Update order status
        PurchaseOrderStatus newStatus = order.calculateReceivingStatus();
        poRepository.updateStatus(orderId, newStatus);
        order.setStatus(newStatus);

        receipts.put(receipt.getId(), receipt);
        return receipt;
    }

    @Override
    public synchronized List<PurchaseOrderReceipt> findReceiptsByOrderId(Long orderId) {
        if (orderId == null || orderId <= 0) {
            return List.of();
        }
        return receipts.values().stream()
                .filter(r -> orderId.equals(r.getPurchaseOrderId()))
                .sorted(Comparator.comparing(PurchaseOrderReceipt::getReceiptDate).reversed()
                        .thenComparing(Comparator.comparing(PurchaseOrderReceipt::getId).reversed()))
                .toList();
    }

    @Override
    public synchronized Optional<PurchaseOrderReceipt> findByReceiptNumber(String receiptNumber) {
        if (receiptNumber == null || receiptNumber.isBlank()) {
            return Optional.empty();
        }
        String clean = receiptNumber.trim();
        return receipts.values().stream()
                .filter(r -> clean.equalsIgnoreCase(r.getReceiptNumber()))
                .findFirst();
    }

    @Override
    public synchronized Map<Long, Integer> getReceivedQuantitiesForOrder(Long orderId) {
        Map<Long, Integer> map = new HashMap<>();
        if (orderId == null || orderId <= 0) {
            return map;
        }
        for (PurchaseOrderReceipt r : receipts.values()) {
            if (orderId.equals(r.getPurchaseOrderId())) {
                for (PurchaseOrderReceiptItem item : r.getItems()) {
                    map.merge(item.getPurchaseOrderItemId(), item.getQuantity(), Integer::sum);
                }
            }
        }
        return map;
    }

    @Override
    public synchronized Map<Long, Map<Long, Integer>> getReceivedQuantitiesForOrders(List<Long> orderIds) {
        Map<Long, Map<Long, Integer>> result = new HashMap<>();
        if (orderIds == null || orderIds.isEmpty()) {
            return result;
        }
        for (Long orderId : orderIds) {
            result.put(orderId, getReceivedQuantitiesForOrder(orderId));
        }
        return result;
    }
}
