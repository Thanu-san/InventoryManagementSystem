package com.stockflow.repository;

import com.stockflow.exception.ValidationException;
import com.stockflow.model.InventoryTransaction;
import com.stockflow.model.Product;
import com.stockflow.model.PurchaseOrder;
import com.stockflow.model.PurchaseOrderReceipt;
import com.stockflow.model.PurchaseOrderReceiptItem;
import com.stockflow.model.PurchaseOrderStatus;
import com.stockflow.model.ReturnLineInput;
import com.stockflow.model.SupplierReturn;
import com.stockflow.model.SupplierReturnItem;
import com.stockflow.model.TransactionType;

import java.util.ArrayList;
import java.util.Collections;
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
 * Thread-safe in-memory implementation of SupplierReturnRepository for unit testing and offline development.
 * Coordinates stock reductions, STOCK_OUT audit transaction recording, and receipt item validations.
 */
public class InMemorySupplierReturnRepository implements SupplierReturnRepository {

    private final PurchaseOrderRepository poRepository;
    private final PurchaseOrderReceiptRepository receiptRepository;
    private final ProductRepository productRepository;
    private final InventoryTransactionRepository transactionRepository;

    private final Map<Long, SupplierReturn> returns = new ConcurrentHashMap<>();
    private final AtomicLong returnIdSequence = new AtomicLong(1);
    private final AtomicLong returnItemIdSequence = new AtomicLong(1);

    public InMemorySupplierReturnRepository(PurchaseOrderRepository poRepository,
                                            PurchaseOrderReceiptRepository receiptRepository,
                                            ProductRepository productRepository,
                                            InventoryTransactionRepository transactionRepository) {
        if (poRepository == null) {
            throw new IllegalArgumentException("PurchaseOrderRepository cannot be null.");
        }
        if (receiptRepository == null) {
            throw new IllegalArgumentException("PurchaseOrderReceiptRepository cannot be null.");
        }
        if (productRepository == null) {
            throw new IllegalArgumentException("ProductRepository cannot be null.");
        }
        if (transactionRepository == null) {
            throw new IllegalArgumentException("InventoryTransactionRepository cannot be null.");
        }
        this.poRepository = poRepository;
        this.receiptRepository = receiptRepository;
        this.productRepository = productRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public synchronized SupplierReturn recordReturn(Long orderId, String returnNumber, String reason, List<ReturnLineInput> lines) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("Purchase order ID must be greater than zero.");
        }
        if (returnNumber == null || returnNumber.isBlank()) {
            throw new ValidationException("Return number cannot be blank.");
        }
        String cleanReturnNumber = returnNumber.trim();
        if (findByReturnNumber(cleanReturnNumber).isPresent()) {
            throw new ValidationException("Supplier return already exists with return number: " + cleanReturnNumber);
        }
        if (lines == null || lines.isEmpty()) {
            throw new ValidationException("Cannot record return with no return line items.");
        }

        PurchaseOrder order = poRepository.findById(orderId)
                .orElseThrow(() -> new ValidationException("Purchase order not found with ID: " + orderId));

        if (order.getStatus() == PurchaseOrderStatus.DRAFT) {
            throw new ValidationException("Cannot return goods against DRAFT purchase order: " + order.getOrderNumber());
        }
        if (order.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new ValidationException("Cannot return goods against CANCELLED purchase order: " + order.getOrderNumber());
        }

        List<PurchaseOrderReceipt> receipts = receiptRepository.findReceiptsByOrderId(orderId);
        if (receipts.isEmpty()) {
            throw new ValidationException("Cannot return goods because no delivery receipts exist for purchase order: " + order.getOrderNumber());
        }

        Map<Long, PurchaseOrderReceiptItem> receiptItemMap = new HashMap<>();
        for (PurchaseOrderReceipt r : receipts) {
            for (PurchaseOrderReceiptItem item : r.getItems()) {
                receiptItemMap.put(item.getId(), item);
            }
        }

        Map<Long, Integer> cumulativeReturnedMap = getReturnedQuantitiesForOrder(orderId);

        // Pre-validate all requested lines before making any modifications
        Set<Long> seenReceiptItemIds = new HashSet<>();
        for (ReturnLineInput line : lines) {
            if (line == null) {
                throw new ValidationException("Return line cannot be null.");
            }
            if (!seenReceiptItemIds.add(line.purchaseOrderReceiptItemId())) {
                throw new ValidationException("Duplicate return line for receipt item ID: " + line.purchaseOrderReceiptItemId());
            }

            PurchaseOrderReceiptItem receiptItem = receiptItemMap.get(line.purchaseOrderReceiptItemId());
            if (receiptItem == null) {
                throw new ValidationException("Receipt line not found on purchase order '" + order.getOrderNumber() + "': " + line.purchaseOrderReceiptItemId());
            }

            if (line.quantity() <= 0) {
                throw new ValidationException("Return quantity must be greater than zero.");
            }

            int previouslyReturned = cumulativeReturnedMap.getOrDefault(receiptItem.getId(), 0);
            int returnable = Math.max(0, receiptItem.getQuantity() - previouslyReturned);

            if (line.quantity() > returnable) {
                throw new ValidationException(
                        "Return quantity (" + line.quantity() + ") exceeds returnable quantity (" +
                        returnable + ") for receipt line ID: " + receiptItem.getId());
            }

            Product product = productRepository.findAll().stream()
                    .filter(p -> receiptItem.getProductId().equals(p.getId()))
                    .findFirst()
                    .orElseThrow(() -> new ValidationException("Product not found with ID: " + receiptItem.getProductId()));

            if (product.getQuantity() < line.quantity()) {
                throw new ValidationException(
                        "Insufficient stock to return product '" + product.getSku() +
                        "'. Available in stock: " + product.getQuantity() + ", Requested return: " + line.quantity());
            }
        }

        // Create return header
        SupplierReturn supplierReturn = new SupplierReturn(cleanReturnNumber, orderId, reason);
        supplierReturn.setId(returnIdSequence.getAndIncrement());

        // Process each return line item: deduct stock, record STOCK_OUT tx, add return item
        for (ReturnLineInput line : lines) {
            PurchaseOrderReceiptItem receiptItem = receiptItemMap.get(line.purchaseOrderReceiptItemId());

            // 1. Record stock movement and STOCK_OUT audit transaction
            InventoryTransaction tx = transactionRepository.recordMovement(
                    receiptItem.getProductId(), TransactionType.STOCK_OUT, line.quantity());

            // 2. Create supplier return item
            SupplierReturnItem returnItem = new SupplierReturnItem(
                    returnItemIdSequence.getAndIncrement(),
                    supplierReturn.getId(),
                    receiptItem.getId(),
                    receiptItem.getProductId(),
                    line.quantity(),
                    line.reason(),
                    tx.getId()
            );
            supplierReturn.addItem(returnItem);
        }

        returns.put(supplierReturn.getId(), supplierReturn);
        return supplierReturn;
    }

    @Override
    public synchronized List<SupplierReturn> findReturnsByOrderId(Long orderId) {
        if (orderId == null || orderId <= 0) {
            return Collections.emptyList();
        }
        return returns.values().stream()
                .filter(r -> orderId.equals(r.getPurchaseOrderId()))
                .sorted(Comparator.comparing(SupplierReturn::getReturnDate).reversed()
                        .thenComparing(Comparator.comparing(SupplierReturn::getId).reversed()))
                .toList();
    }

    @Override
    public synchronized Optional<SupplierReturn> findByReturnNumber(String returnNumber) {
        if (returnNumber == null || returnNumber.isBlank()) {
            return Optional.empty();
        }
        String clean = returnNumber.trim();
        return returns.values().stream()
                .filter(r -> clean.equalsIgnoreCase(r.getReturnNumber()))
                .findFirst();
    }

    @Override
    public synchronized Optional<SupplierReturn> findById(Long id) {
        if (id == null || id <= 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(returns.get(id));
    }

    @Override
    public synchronized Map<Long, Integer> getReturnedQuantitiesForOrder(Long orderId) {
        Map<Long, Integer> map = new HashMap<>();
        if (orderId == null || orderId <= 0) {
            return map;
        }
        for (SupplierReturn r : returns.values()) {
            if (orderId.equals(r.getPurchaseOrderId())) {
                for (SupplierReturnItem item : r.getItems()) {
                    map.merge(item.getPurchaseOrderReceiptItemId(), item.getQuantity(), Integer::sum);
                }
            }
        }
        return map;
    }

    @Override
    public synchronized Map<Long, Integer> getReturnedQuantitiesForReceiptItems(List<Long> receiptItemIds) {
        Map<Long, Integer> map = new HashMap<>();
        if (receiptItemIds == null || receiptItemIds.isEmpty()) {
            return map;
        }
        Set<Long> idSet = new HashSet<>(receiptItemIds);
        for (SupplierReturn r : returns.values()) {
            for (SupplierReturnItem item : r.getItems()) {
                if (idSet.contains(item.getPurchaseOrderReceiptItemId())) {
                    map.merge(item.getPurchaseOrderReceiptItemId(), item.getQuantity(), Integer::sum);
                }
            }
        }
        return map;
    }

    @Override
    public synchronized boolean existsByReturnNumber(String returnNumber) {
        if (returnNumber == null || returnNumber.isBlank()) {
            return false;
        }
        String clean = returnNumber.trim();
        return returns.values().stream()
                .anyMatch(r -> clean.equalsIgnoreCase(r.getReturnNumber()));
    }

    @Override
    public synchronized boolean existsByProductId(Long productId) {
        if (productId == null || productId <= 0) {
            return false;
        }
        return returns.values().stream()
                .flatMap(r -> r.getItems().stream())
                .anyMatch(item -> productId.equals(item.getProductId()));
    }
}
