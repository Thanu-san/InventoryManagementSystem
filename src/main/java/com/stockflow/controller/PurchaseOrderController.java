package com.stockflow.controller;

import com.stockflow.exception.DatabaseException;
import com.stockflow.exception.ValidationException;
import com.stockflow.model.PurchaseOrder;
import com.stockflow.model.PurchaseOrderItem;
import com.stockflow.model.PurchaseOrderReceipt;
import com.stockflow.model.PurchaseOrderReceiptItem;
import com.stockflow.model.PurchaseOrderStatus;
import com.stockflow.model.ReceiptLineInput;
import com.stockflow.model.Supplier;
import com.stockflow.service.PurchaseOrderService;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

/**
 * Controller responsible for Purchase Order Management and Receiving console interactions.
 * Accepts user input, delegates to PurchaseOrderService, and renders formatted order and receipt information.
 */
public class PurchaseOrderController {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PurchaseOrderService purchaseOrderService;
    private final Scanner scanner;

    public PurchaseOrderController(PurchaseOrderService purchaseOrderService, Scanner scanner) {
        this.purchaseOrderService = purchaseOrderService;
        this.scanner = scanner;
    }

    /**
     * Runs the Purchase Order Management menu loop.
     */
    public void runMenu() {
        boolean back = false;
        while (!back) {
            System.out.println("\n--- Purchase Order Management ---");
            System.out.println("1. Create Draft Purchase Order");
            System.out.println("2. Add or Update Product Line Item");
            System.out.println("3. Remove Product Line Item");
            System.out.println("4. View Purchase Order Details");
            System.out.println("5. List Purchase Orders");
            System.out.println("6. Place Purchase Order");
            System.out.println("7. Cancel Purchase Order");
            System.out.println("8. Receive Goods Against Purchase Order");
            System.out.println("9. View Receiving History for Order");
            System.out.println("10. Back to Main Menu");
            System.out.print("Select an option: ");

            if (!scanner.hasNextLine()) {
                break;
            }
            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1" -> handleCreateDraftOrder();
                case "2" -> handleAddOrUpdateItem();
                case "3" -> handleRemoveItem();
                case "4" -> handleViewOrder();
                case "5" -> handleListOrders();
                case "6" -> handlePlaceOrder();
                case "7" -> handleCancelOrder();
                case "8" -> handleReceiveGoods();
                case "9" -> handleViewReceiptHistory();
                case "10" -> back = true;
                default -> System.out.println("Invalid option. Please try again.");
            }
        }
    }

    private void handleCreateDraftOrder() {
        System.out.println("\n[ Create Draft Purchase Order ]");

        Optional<String> supplierCodeOpt = promptNonBlankString("Enter Supplier Code: ", "Supplier code");
        if (supplierCodeOpt.isEmpty()) {
            return;
        }

        Optional<String> orderNumberOpt = readLine("Enter Custom Order Number (optional, press Enter to auto-generate): ");
        if (orderNumberOpt.isEmpty()) {
            return;
        }

        try {
            String customNumber = orderNumberOpt.get().trim();
            PurchaseOrder order;
            if (customNumber.isEmpty()) {
                order = purchaseOrderService.createDraftOrder(supplierCodeOpt.get());
            } else {
                order = purchaseOrderService.createDraftOrder(customNumber, supplierCodeOpt.get());
            }
            System.out.println("Success: Draft purchase order created successfully! Order Number: " + order.getOrderNumber());
        } catch (ValidationException e) {
            System.out.println("Validation Error: " + e.getMessage());
        } catch (DatabaseException e) {
            System.out.println("Database Error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handleAddOrUpdateItem() {
        System.out.println("\n[ Add / Update Line Item ]");

        Optional<String> orderNumOpt = promptNonBlankString("Enter Order Number: ", "Order number");
        if (orderNumOpt.isEmpty()) return;

        Optional<String> skuOpt = promptNonBlankString("Enter Product SKU: ", "Product SKU");
        if (skuOpt.isEmpty()) return;

        Optional<Integer> qtyOpt = promptPositiveInteger("Enter Quantity to Order: ");
        if (qtyOpt.isEmpty()) return;

        Optional<BigDecimal> costOpt = promptNonNegativeBigDecimal("Enter Agreed Unit Purchase Cost: ");
        if (costOpt.isEmpty()) return;

        try {
            PurchaseOrderItem item = purchaseOrderService.addOrUpdateItem(
                    orderNumOpt.get(), skuOpt.get(), qtyOpt.get(), costOpt.get());
            System.out.println("Success: Line item saved successfully!");
            System.out.println(String.format("  SKU: %s | Product: %s | Qty: %d | Unit Cost: $%.2f | Line Total: $%.2f",
                    item.getProductSku(), item.getProductName(), item.getOrderedQuantity(),
                    item.getUnitCost(), item.getLineTotal()));
        } catch (ValidationException e) {
            System.out.println("Validation Error: " + e.getMessage());
        } catch (DatabaseException e) {
            System.out.println("Database Error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handleRemoveItem() {
        System.out.println("\n[ Remove Line Item ]");

        Optional<String> orderNumOpt = promptNonBlankString("Enter Order Number: ", "Order number");
        if (orderNumOpt.isEmpty()) return;

        Optional<String> skuOpt = promptNonBlankString("Enter Product SKU to Remove: ", "Product SKU");
        if (skuOpt.isEmpty()) return;

        while (true) {
            System.out.print("Are you sure you want to remove product '" + skuOpt.get() + "' from order '" + orderNumOpt.get() + "'? (Y/N): ");
            if (!scanner.hasNextLine()) return;
            String confirm = scanner.nextLine().trim();
            if (confirm.equalsIgnoreCase("y")) {
                try {
                    purchaseOrderService.removeItem(orderNumOpt.get(), skuOpt.get());
                    System.out.println("Success: Product line removed successfully from purchase order.");
                } catch (ValidationException e) {
                    System.out.println("Validation Error: " + e.getMessage());
                } catch (DatabaseException e) {
                    System.out.println("Database Error: " + e.getMessage());
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
                break;
            } else if (confirm.equalsIgnoreCase("n")) {
                System.out.println("Removal cancelled.");
                break;
            } else {
                System.out.println("Please enter Y or N.");
            }
        }
    }

    private void handleViewOrder() {
        System.out.println("\n[ View Purchase Order Details ]");

        Optional<String> orderNumOpt = promptNonBlankString("Enter Order Number: ", "Order number");
        if (orderNumOpt.isEmpty()) return;

        try {
            Optional<PurchaseOrder> orderOpt = purchaseOrderService.getOrderByNumber(orderNumOpt.get());
            if (orderOpt.isEmpty()) {
                System.out.println("Purchase order not found with order number: " + orderNumOpt.get());
                return;
            }

            PurchaseOrder order = orderOpt.get();
            printOrderDetails(order);
        } catch (ValidationException e) {
            System.out.println("Validation Error: " + e.getMessage());
        } catch (DatabaseException e) {
            System.out.println("Database Error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handleListOrders() {
        System.out.println("\n[ List Purchase Orders ]");
        System.out.println("Filter options: 1. All Orders | 2. DRAFT | 3. PLACED | 4. PARTIALLY_RECEIVED | 5. RECEIVED | 6. CANCELLED");
        System.out.print("Select filter (default: 1): ");

        if (!scanner.hasNextLine()) return;
        String filterChoice = scanner.nextLine().trim();

        try {
            List<PurchaseOrder> orders = switch (filterChoice) {
                case "2" -> purchaseOrderService.getOrdersByStatus(PurchaseOrderStatus.DRAFT);
                case "3" -> purchaseOrderService.getOrdersByStatus(PurchaseOrderStatus.PLACED);
                case "4" -> purchaseOrderService.getOrdersByStatus(PurchaseOrderStatus.PARTIALLY_RECEIVED);
                case "5" -> purchaseOrderService.getOrdersByStatus(PurchaseOrderStatus.RECEIVED);
                case "6" -> purchaseOrderService.getOrdersByStatus(PurchaseOrderStatus.CANCELLED);
                default -> purchaseOrderService.getAllOrders();
            };

            if (orders.isEmpty()) {
                System.out.println("No purchase orders found.");
                return;
            }

            System.out.println("\n" + "=".repeat(110));
            System.out.println(String.format("%-18s | %-12s | %-18s | %-6s | %-8s | %-8s | %-12s | %s",
                    "Order Number", "Supplier", "Status", "Lines", "Ordered", "Received", "Total ($)", "Created At"));
            System.out.println("-".repeat(110));

            for (PurchaseOrder po : orders) {
                String supplierDisplay = resolveSupplierCode(po);
                System.out.println(String.format("%-18s | %-12s | %-18s | %-6d | %-8d | %-8d | %-12.2f | %s",
                        po.getOrderNumber(),
                        supplierDisplay,
                        po.getStatus(),
                        po.getItems().size(),
                        po.getTotalUnits(),
                        po.getTotalReceivedUnits(),
                        po.getTotalAmount(),
                        po.getCreatedAt().format(DATE_FORMATTER)));
            }
            System.out.println("=".repeat(110));
            System.out.println("Total Orders Listed: " + orders.size());
        } catch (ValidationException e) {
            System.out.println("Validation Error: " + e.getMessage());
        } catch (DatabaseException e) {
            System.out.println("Database Error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handlePlaceOrder() {
        System.out.println("\n[ Place Purchase Order ]");

        Optional<String> orderNumOpt = promptNonBlankString("Enter Order Number to Place: ", "Order number");
        if (orderNumOpt.isEmpty()) return;

        while (true) {
            System.out.print("Are you sure you want to place order '" + orderNumOpt.get() + "'? (Y/N): ");
            if (!scanner.hasNextLine()) return;
            String confirm = scanner.nextLine().trim();
            if (confirm.equalsIgnoreCase("y")) {
                try {
                    PurchaseOrder placed = purchaseOrderService.placeOrder(orderNumOpt.get());
                    System.out.println("Success: Purchase order '" + placed.getOrderNumber() + "' has been successfully placed!");
                } catch (ValidationException e) {
                    System.out.println("Validation Error: " + e.getMessage());
                } catch (DatabaseException e) {
                    System.out.println("Database Error: " + e.getMessage());
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
                break;
            } else if (confirm.equalsIgnoreCase("n")) {
                System.out.println("Placement cancelled.");
                break;
            } else {
                System.out.println("Please enter Y or N.");
            }
        }
    }

    private void handleCancelOrder() {
        System.out.println("\n[ Cancel Purchase Order ]");

        Optional<String> orderNumOpt = promptNonBlankString("Enter Order Number to Cancel: ", "Order number");
        if (orderNumOpt.isEmpty()) return;

        while (true) {
            System.out.print("Are you sure you want to cancel order '" + orderNumOpt.get() + "'? (Y/N): ");
            if (!scanner.hasNextLine()) return;
            String confirm = scanner.nextLine().trim();
            if (confirm.equalsIgnoreCase("y")) {
                try {
                    PurchaseOrder cancelled = purchaseOrderService.cancelOrder(orderNumOpt.get());
                    System.out.println("Success: Purchase order '" + cancelled.getOrderNumber() + "' has been cancelled.");
                } catch (ValidationException e) {
                    System.out.println("Validation Error: " + e.getMessage());
                } catch (DatabaseException e) {
                    System.out.println("Database Error: " + e.getMessage());
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
                break;
            } else if (confirm.equalsIgnoreCase("n")) {
                System.out.println("Cancellation aborted.");
                break;
            } else {
                System.out.println("Please enter Y or N.");
            }
        }
    }

    private void handleReceiveGoods() {
        System.out.println("\n[ Receive Goods Against Purchase Order ]");

        Optional<String> orderNumOpt = promptNonBlankString("Enter Order Number: ", "Order number");
        if (orderNumOpt.isEmpty()) return;

        try {
            Optional<PurchaseOrder> orderOpt = purchaseOrderService.getOrderByNumber(orderNumOpt.get());
            if (orderOpt.isEmpty()) {
                System.out.println("Purchase order not found with order number: " + orderNumOpt.get());
                return;
            }

            PurchaseOrder order = orderOpt.get();
            if (!order.isReceivable()) {
                if (order.getStatus() == PurchaseOrderStatus.RECEIVED) {
                    System.out.println("Order '" + order.getOrderNumber() + "' has already been fully received.");
                } else {
                    System.out.println("Cannot receive goods. Order status is: " + order.getStatus() +
                            ". Only PLACED or PARTIALLY_RECEIVED orders can receive goods.");
                }
                return;
            }

            printOrderDetails(order);

            List<ReceiptLineInput> linesToReceive = new ArrayList<>();
            for (PurchaseOrderItem item : order.getItems()) {
                int remaining = item.getRemainingQuantity();
                if (remaining <= 0) {
                    continue;
                }

                System.out.println(String.format("\nLine: %s - %s | Ordered: %d | Already Received: %d | Remaining: %d",
                        item.getProductSku(), item.getProductName(), item.getOrderedQuantity(), item.getReceivedQuantity(), remaining));

                while (true) {
                    Optional<String> inputOpt = readLine(String.format("Enter quantity to receive now (0 to skip, max %d): ", remaining));
                    if (inputOpt.isEmpty()) return;
                    String input = inputOpt.get().trim();
                    if (input.isEmpty() || input.equals("0")) {
                        break;
                    }
                    try {
                        int qty = Integer.parseInt(input);
                        if (qty < 0) {
                            System.out.println("Error: Quantity cannot be negative. Enter 0 to skip.");
                        } else if (qty > remaining) {
                            System.out.println("Error: Quantity exceeds remaining amount (" + remaining + "). Please try again.");
                        } else {
                            linesToReceive.add(new ReceiptLineInput(item.getId(), qty));
                            break;
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Error: Invalid number format. Please enter a valid integer.");
                    }
                }
            }

            if (linesToReceive.isEmpty()) {
                System.out.println("No items selected for delivery. Receiving aborted.");
                return;
            }

            Optional<String> notesOpt = readLine("Enter delivery / receipt notes (optional, press Enter to skip): ");
            String notes = notesOpt.map(String::trim).filter(s -> !s.isEmpty()).orElse(null);

            // Review proposed delivery
            System.out.println("\n--- Proposed Delivery Summary ---");
            for (ReceiptLineInput line : linesToReceive) {
                PurchaseOrderItem item = order.findItemById(line.purchaseOrderItemId()).orElseThrow();
                System.out.println(String.format("  SKU: %-15s | Product: %-25s | Receiving: %d",
                        item.getProductSku(), item.getProductName(), line.quantity()));
            }

            while (true) {
                System.out.print("Confirm receiving goods? (Y/N): ");
                if (!scanner.hasNextLine()) return;
                String confirm = scanner.nextLine().trim();
                if (confirm.equalsIgnoreCase("y")) {
                    PurchaseOrderReceipt receipt = purchaseOrderService.receiveGoods(order.getOrderNumber(), linesToReceive, notes);
                    System.out.println("Success: Goods successfully received! Receipt Number: " + receipt.getReceiptNumber());

                    Optional<PurchaseOrder> updatedOrder = purchaseOrderService.getOrderByNumber(order.getOrderNumber());
                    updatedOrder.ifPresent(po -> System.out.println("Updated Purchase Order Status: " + po.getStatus()));
                    break;
                } else if (confirm.equalsIgnoreCase("n")) {
                    System.out.println("Receiving aborted. No stock changes applied.");
                    break;
                } else {
                    System.out.println("Please enter Y or N.");
                }
            }
        } catch (ValidationException e) {
            System.out.println("Validation Error: " + e.getMessage());
        } catch (DatabaseException e) {
            System.out.println("Database Error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handleViewReceiptHistory() {
        System.out.println("\n[ View Receiving History for Order ]");

        Optional<String> orderNumOpt = promptNonBlankString("Enter Order Number: ", "Order number");
        if (orderNumOpt.isEmpty()) return;

        try {
            List<PurchaseOrderReceipt> receipts = purchaseOrderService.getReceiptsForOrder(orderNumOpt.get());
            if (receipts.isEmpty()) {
                System.out.println("No receiving history found for order: " + orderNumOpt.get());
                return;
            }

            System.out.println("\n" + "=".repeat(85));
            System.out.println("Receiving History for Order: " + orderNumOpt.get());
            System.out.println("=".repeat(85));

            int receiptIndex = 1;
            for (PurchaseOrderReceipt r : receipts) {
                System.out.println(String.format("Delivery #%d | Receipt Number: %s | Date: %s | Notes: %s",
                        receiptIndex++,
                        r.getReceiptNumber(),
                        r.getReceiptDate().format(DATE_FORMATTER),
                        r.getNotes() != null ? r.getNotes() : "-"));
                System.out.println("-".repeat(85));
                System.out.println(String.format("  %-10s | %-12s | %-12s | %s",
                        "Item ID", "Product ID", "Quantity", "Inventory Tx ID"));
                for (PurchaseOrderReceiptItem ri : r.getItems()) {
                    System.out.println(String.format("  %-10d | %-12d | %-12d | %s",
                            ri.getPurchaseOrderItemId(),
                            ri.getProductId(),
                            ri.getQuantity(),
                            ri.getInventoryTransactionId() != null ? "#" + ri.getInventoryTransactionId() : "-"));
                }
                System.out.println(String.format("  Total Quantity Received in this delivery: %d units", r.getTotalReceivedQuantity()));
                System.out.println("=".repeat(85));
            }
        } catch (ValidationException e) {
            System.out.println("Validation Error: " + e.getMessage());
        } catch (DatabaseException e) {
            System.out.println("Database Error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void printOrderDetails(PurchaseOrder order) {
        String supplierDisplay = resolveSupplierCode(order);
        System.out.println("\n" + "=".repeat(100));
        System.out.println("Purchase Order Details");
        System.out.println("=".repeat(100));
        System.out.println("Order Number : " + order.getOrderNumber());
        System.out.println("Supplier     : " + supplierDisplay);
        System.out.println("Status       : " + order.getStatus());
        System.out.println("Created At   : " + order.getCreatedAt().format(DATE_FORMATTER));
        System.out.println("-".repeat(100));

        List<PurchaseOrderItem> items = order.getItems();
        if (items.isEmpty()) {
            System.out.println("No line items currently added to this order.");
        } else {
            System.out.println(String.format("%-4s | %-15s | %-25s | %-7s | %-8s | %-9s | %-10s | %s",
                    "#", "SKU", "Product Name", "Ordered", "Received", "Remaining", "Unit Cost", "Line Total"));
            System.out.println("-".repeat(100));
            int index = 1;
            for (PurchaseOrderItem item : items) {
                System.out.println(String.format("%-4d | %-15s | %-25s | %-7d | %-8d | %-9d | $%-9.2f | $%.2f",
                        index++,
                        item.getProductSku(),
                        item.getProductName(),
                        item.getOrderedQuantity(),
                        item.getReceivedQuantity(),
                        item.getRemainingQuantity(),
                        item.getUnitCost(),
                        item.getLineTotal()));
            }
        }
        System.out.println("=".repeat(100));
        System.out.println(String.format("Total Units Ordered: %d | Total Received: %d | Total Remaining: %d | Total Amount: $%.2f",
                order.getTotalUnits(), order.getTotalReceivedUnits(), order.getTotalRemainingUnits(), order.getTotalAmount()));
        System.out.println("=".repeat(100));
    }

    private String resolveSupplierCode(PurchaseOrder order) {
        Optional<Supplier> supplierOpt = purchaseOrderService.getSupplierForOrder(order);
        if (supplierOpt.isPresent()) {
            Supplier s = supplierOpt.get();
            return s.getSupplierCode() + " (" + s.getName() + ")";
        }
        return "ID: " + order.getSupplierId();
    }

    // --- Reusable Console Input Helpers ---

    private Optional<String> readLine(String prompt) {
        System.out.print(prompt);
        if (!scanner.hasNextLine()) {
            return Optional.empty();
        }
        return Optional.of(scanner.nextLine());
    }

    private Optional<String> promptNonBlankString(String prompt, String fieldName) {
        while (true) {
            Optional<String> lineOpt = readLine(prompt);
            if (lineOpt.isEmpty()) {
                return Optional.empty();
            }
            String input = lineOpt.get().trim();
            if (!input.isEmpty()) {
                return Optional.of(input);
            }
            System.out.println("Error: " + fieldName + " cannot be blank. Please try again.");
        }
    }

    private Optional<Integer> promptPositiveInteger(String prompt) {
        while (true) {
            Optional<String> lineOpt = readLine(prompt);
            if (lineOpt.isEmpty()) {
                return Optional.empty();
            }
            try {
                int value = Integer.parseInt(lineOpt.get().trim());
                if (value > 0) {
                    return Optional.of(value);
                }
                System.out.println("Error: Quantity must be greater than zero. Please try again.");
            } catch (NumberFormatException e) {
                System.out.println("Error: Invalid number format. Please enter a valid whole number.");
            }
        }
    }

    private Optional<BigDecimal> promptNonNegativeBigDecimal(String prompt) {
        while (true) {
            Optional<String> lineOpt = readLine(prompt);
            if (lineOpt.isEmpty()) {
                return Optional.empty();
            }
            try {
                BigDecimal value = new BigDecimal(lineOpt.get().trim());
                if (value.compareTo(BigDecimal.ZERO) >= 0) {
                    return Optional.of(value);
                }
                System.out.println("Error: Unit cost must be greater than or equal to zero. Please try again.");
            } catch (NumberFormatException e) {
                System.out.println("Error: Invalid amount format. Please enter a valid decimal number.");
            }
        }
    }
}
