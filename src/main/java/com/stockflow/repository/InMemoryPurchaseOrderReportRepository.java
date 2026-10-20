package com.stockflow.repository;

import com.stockflow.exception.ValidationException;
import com.stockflow.model.OutstandingPurchaseOrderReportItem;
import com.stockflow.model.PurchaseOrder;
import com.stockflow.model.PurchaseOrderItem;
import com.stockflow.model.PurchaseOrderReceipt;
import com.stockflow.model.PurchaseOrderReceiptItem;
import com.stockflow.model.PurchaseOrderStatus;
import com.stockflow.model.ReceivingHistoryReportItem;
import com.stockflow.model.Supplier;
import com.stockflow.model.SupplierPurchasingSummaryItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Thread-safe in-memory implementation of PurchaseOrderReportRepository for unit testing and offline development.
 * Aggregates reporting statistics cleanly without data mutation or join multiplication.
 */
public class InMemoryPurchaseOrderReportRepository implements PurchaseOrderReportRepository {

    private final PurchaseOrderRepository poRepository;
    private final SupplierRepository supplierRepository;
    private final PurchaseOrderReceiptRepository receiptRepository;

    public InMemoryPurchaseOrderReportRepository(PurchaseOrderRepository poRepository,
                                                SupplierRepository supplierRepository,
                                                PurchaseOrderReceiptRepository receiptRepository) {
        if (poRepository == null) {
            throw new IllegalArgumentException("PurchaseOrderRepository cannot be null.");
        }
        if (supplierRepository == null) {
            throw new IllegalArgumentException("SupplierRepository cannot be null.");
        }
        this.poRepository = poRepository;
        this.supplierRepository = supplierRepository;
        this.receiptRepository = receiptRepository;
    }

    @Override
    public synchronized List<OutstandingPurchaseOrderReportItem> getOutstandingPurchaseOrdersReport() {
        List<PurchaseOrder> outstandingOrders = poRepository.findAll().stream()
                .filter(po -> po.getStatus() == PurchaseOrderStatus.PLACED ||
                              po.getStatus() == PurchaseOrderStatus.PARTIALLY_RECEIVED)
                .sorted(Comparator.comparing(PurchaseOrder::getCreatedAt)
                        .thenComparing(PurchaseOrder::getOrderNumber))
                .toList();

        List<OutstandingPurchaseOrderReportItem> report = new ArrayList<>();
        for (PurchaseOrder po : outstandingOrders) {
            Optional<Supplier> supplierOpt = supplierRepository.findById(po.getSupplierId());
            String supplierCode = supplierOpt.map(Supplier::getSupplierCode).orElse("UNKNOWN");
            String supplierName = supplierOpt.map(Supplier::getName).orElse("Unknown Supplier");

            int distinctLines = po.getItems().size();
            int orderedUnits = po.getTotalUnits();
            int receivedUnits = po.getTotalReceivedUnits();
            int remainingUnits = po.getTotalRemainingUnits();

            report.add(new OutstandingPurchaseOrderReportItem(
                    po.getOrderNumber(),
                    supplierCode,
                    supplierName,
                    po.getCreatedAt(),
                    distinctLines,
                    orderedUnits,
                    receivedUnits,
                    remainingUnits,
                    po.getStatus()
            ));
        }
        return Collections.unmodifiableList(report);
    }

    @Override
    public synchronized List<ReceivingHistoryReportItem> getReceivingHistoryReport(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) {
            throw new ValidationException("Start date cannot be null.");
        }
        if (endDate == null) {
            throw new ValidationException("End date cannot be null.");
        }
        if (startDate.isAfter(endDate)) {
            throw new ValidationException("Start date cannot be after end date.");
        }

        if (receiptRepository == null) {
            return Collections.emptyList();
        }

        List<PurchaseOrder> allOrders = poRepository.findAll();
        Map<Long, PurchaseOrder> orderMap = new HashMap<>();
        Map<Long, Supplier> supplierMap = new HashMap<>();

        for (PurchaseOrder po : allOrders) {
            orderMap.put(po.getId(), po);
            if (!supplierMap.containsKey(po.getSupplierId())) {
                supplierRepository.findById(po.getSupplierId()).ifPresent(s -> supplierMap.put(s.getId(), s));
            }
        }

        List<ReceivingHistoryReportItem> items = new ArrayList<>();

        for (PurchaseOrder po : allOrders) {
            List<PurchaseOrderReceipt> receipts = receiptRepository.findReceiptsByOrderId(po.getId());
            for (PurchaseOrderReceipt receipt : receipts) {
                LocalDate rDate = receipt.getReceiptDate().toLocalDate();
                if (rDate.isBefore(startDate) || rDate.isAfter(endDate)) {
                    continue;
                }

                Supplier supplier = supplierMap.get(po.getSupplierId());
                String supplierName = supplier != null ? supplier.getName() : "Unknown Supplier";

                for (PurchaseOrderReceiptItem receiptItem : receipt.getItems()) {
                    Optional<PurchaseOrderItem> orderItemOpt = po.findItemById(receiptItem.getPurchaseOrderItemId());
                    String sku = orderItemOpt.map(PurchaseOrderItem::getProductSku).orElse("UNKNOWN");
                    String name = orderItemOpt.map(PurchaseOrderItem::getProductName).orElse("Unknown Product");
                    BigDecimal agreedCost = orderItemOpt.map(PurchaseOrderItem::getUnitCost).orElse(BigDecimal.ZERO);
                    BigDecimal lineCost = agreedCost.multiply(BigDecimal.valueOf(receiptItem.getQuantity()));

                    items.add(new ReceivingHistoryReportItem(
                            receipt.getReceiptNumber(),
                            po.getOrderNumber(),
                            supplierName,
                            receipt.getReceiptDate(),
                            sku,
                            name,
                            receiptItem.getQuantity(),
                            agreedCost,
                            lineCost
                    ));
                }
            }
        }

        items.sort(Comparator.comparing(ReceivingHistoryReportItem::getReceiptDate)
                .thenComparing(ReceivingHistoryReportItem::getReceiptNumber)
                .thenComparing(ReceivingHistoryReportItem::getProductSku));

        return Collections.unmodifiableList(items);
    }

    @Override
    public synchronized List<SupplierPurchasingSummaryItem> getSupplierPurchasingSummary() {
        List<Supplier> suppliers = supplierRepository.findAll().stream()
                .sorted(Comparator.comparing(Supplier::getSupplierCode))
                .toList();

        List<PurchaseOrder> allOrders = poRepository.findAll();

        List<SupplierPurchasingSummaryItem> summary = new ArrayList<>();

        for (Supplier supplier : suppliers) {
            List<PurchaseOrder> committedOrders = allOrders.stream()
                    .filter(po -> supplier.getId().equals(po.getSupplierId()))
                    .filter(po -> po.getStatus() == PurchaseOrderStatus.PLACED ||
                                  po.getStatus() == PurchaseOrderStatus.PARTIALLY_RECEIVED ||
                                  po.getStatus() == PurchaseOrderStatus.RECEIVED)
                    .toList();

            int orderCount = committedOrders.size();
            int totalOrderedUnits = 0;
            int totalReceivedUnits = 0;
            BigDecimal totalOrderValue = BigDecimal.ZERO;
            BigDecimal totalReceivedValue = BigDecimal.ZERO;

            for (PurchaseOrder po : committedOrders) {
                for (PurchaseOrderItem item : po.getItems()) {
                    totalOrderedUnits += item.getOrderedQuantity();
                    totalOrderValue = totalOrderValue.add(item.getLineTotal());

                    int receivedQty = item.getReceivedQuantity();
                    totalReceivedUnits += receivedQty;
                    totalReceivedValue = totalReceivedValue.add(item.getUnitCost().multiply(BigDecimal.valueOf(receivedQty)));
                }
            }

            int totalOutstandingUnits = totalOrderedUnits - totalReceivedUnits;

            summary.add(new SupplierPurchasingSummaryItem(
                    supplier.getSupplierCode(),
                    supplier.getName(),
                    orderCount,
                    totalOrderedUnits,
                    totalReceivedUnits,
                    totalOutstandingUnits,
                    totalOrderValue,
                    totalReceivedValue
            ));
        }

        return Collections.unmodifiableList(summary);
    }
}
