package com.stockflow.controller;

import com.stockflow.exception.DatabaseException;
import com.stockflow.exception.ValidationException;
import com.stockflow.model.Supplier;
import com.stockflow.service.SupplierService;

import java.util.List;
import java.util.Optional;
import java.util.Scanner;

/**
 * Controller responsible for the Supplier Management console interactions.
 * Receives user input, delegates to SupplierService, and prints formatted output.
 * Provides robust validation, failure recovery, and friendly user error messages.
 */
public class SupplierController {

    private final SupplierService supplierService;
    private final Scanner scanner;

    public SupplierController(SupplierService supplierService, Scanner scanner) {
        this.supplierService = supplierService;
        this.scanner = scanner;
    }

    /**
     * Runs the Supplier Management menu loop.
     */
    public void runMenu() {
        boolean back = false;
        while (!back) {
            System.out.println("\n--- Supplier Management ---");
            System.out.println("1. Add Supplier");
            System.out.println("2. Find Supplier by Code");
            System.out.println("3. List All Suppliers");
            System.out.println("4. Update Supplier");
            System.out.println("5. Delete Supplier");
            System.out.println("6. Back to Main Menu");
            System.out.print("Select an option: ");

            if (!scanner.hasNextLine()) {
                break;
            }
            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1" -> handleAddSupplier();
                case "2" -> handleFindSupplierByCode();
                case "3" -> handleListSuppliers();
                case "4" -> handleUpdateSupplier();
                case "5" -> handleDeleteSupplier();
                case "6" -> back = true;
                default -> System.out.println("Invalid option. Please try again.");
            }
        }
    }

    private void handleAddSupplier() {
        System.out.println("\n[ Add New Supplier ]");

        Optional<String> codeOpt = promptNonBlankString("Enter Supplier Code: ", "Supplier code");
        if (codeOpt.isEmpty()) {
            return;
        }

        Optional<String> nameOpt = promptNonBlankString("Enter Supplier Name: ", "Supplier name");
        if (nameOpt.isEmpty()) {
            return;
        }

        Optional<String> contactNameOpt = readLine("Enter Contact Name (optional, press Enter to skip): ");
        if (contactNameOpt.isEmpty()) {
            return;
        }

        Optional<String> phoneOpt = readLine("Enter Phone (optional, press Enter to skip): ");
        if (phoneOpt.isEmpty()) {
            return;
        }

        Optional<String> emailOpt = readLine("Enter Email (optional, press Enter to skip): ");
        if (emailOpt.isEmpty()) {
            return;
        }

        Optional<String> addressOpt = readLine("Enter Address (optional, press Enter to skip): ");
        if (addressOpt.isEmpty()) {
            return;
        }

        try {
            Supplier created = supplierService.createSupplier(
                    codeOpt.get(),
                    nameOpt.get(),
                    contactNameOpt.get().isBlank() ? null : contactNameOpt.get(),
                    phoneOpt.get().isBlank() ? null : phoneOpt.get(),
                    emailOpt.get().isBlank() ? null : emailOpt.get(),
                    addressOpt.get().isBlank() ? null : addressOpt.get()
            );
            System.out.println("Success: Supplier created successfully! ID: " + created.getId());
        } catch (ValidationException e) {
            System.out.println("Validation Error: " + e.getMessage());
        } catch (DatabaseException e) {
            System.out.println("Database Error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handleFindSupplierByCode() {
        System.out.println("\n[ Find Supplier by Code ]");
        Optional<String> codeOpt = promptNonBlankString("Enter Supplier Code: ", "Supplier code");
        if (codeOpt.isEmpty()) {
            return;
        }

        String code = codeOpt.get();
        try {
            Optional<Supplier> supplierOpt = supplierService.findSupplierByCode(code);
            if (supplierOpt.isPresent()) {
                printSupplierDetails(supplierOpt.get());
            } else {
                System.out.println("Supplier not found with code: " + code);
            }
        } catch (ValidationException e) {
            System.out.println("Validation Error: " + e.getMessage());
        } catch (DatabaseException e) {
            System.out.println("Database Error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handleListSuppliers() {
        System.out.println("\n[ Supplier List ]");
        try {
            List<Supplier> suppliers = supplierService.getAllSuppliers();

            if (suppliers.isEmpty()) {
                System.out.println("No suppliers found.");
                return;
            }

            System.out.printf("%-6s | %-15s | %-25s | %-20s | %-15s | %-25s%n",
                    "ID", "CODE", "NAME", "CONTACT", "PHONE", "EMAIL");
            System.out.println("-".repeat(115));

            for (Supplier s : suppliers) {
                System.out.printf("%-6d | %-15s | %-25s | %-20s | %-15s | %-25s%n",
                        s.getId(),
                        s.getSupplierCode(),
                        s.getName(),
                        s.getContactName() != null ? s.getContactName() : "-",
                        s.getPhone() != null ? s.getPhone() : "-",
                        s.getEmail() != null ? s.getEmail() : "-");
            }
        } catch (ValidationException e) {
            System.out.println("Validation Error: " + e.getMessage());
        } catch (DatabaseException e) {
            System.out.println("Database Error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handleUpdateSupplier() {
        System.out.println("\n[ Update Supplier ]");
        Optional<String> codeOpt = promptNonBlankString("Enter Supplier Code to update: ", "Supplier code");
        if (codeOpt.isEmpty()) {
            return;
        }

        String code = codeOpt.get();
        Optional<Supplier> supplierOpt;
        try {
            supplierOpt = supplierService.findSupplierByCode(code);
        } catch (ValidationException e) {
            System.out.println("Validation Error: " + e.getMessage());
            return;
        } catch (DatabaseException e) {
            System.out.println("Database Error: " + e.getMessage());
            return;
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            return;
        }

        if (supplierOpt.isEmpty()) {
            System.out.println("Supplier not found with code: " + code);
            return;
        }

        Supplier current = supplierOpt.get();
        System.out.println("\nCurrent Supplier:");
        printSupplierDetails(current);

        Optional<String> newNameOpt = promptNonBlankString("Enter new name: ", "Supplier name");
        if (newNameOpt.isEmpty()) {
            return;
        }

        Optional<String> newContactOpt = readLine("Enter new contact name (optional, press Enter to skip): ");
        if (newContactOpt.isEmpty()) {
            return;
        }

        Optional<String> newPhoneOpt = readLine("Enter new phone (optional, press Enter to skip): ");
        if (newPhoneOpt.isEmpty()) {
            return;
        }

        Optional<String> newEmailOpt = readLine("Enter new email (optional, press Enter to skip): ");
        if (newEmailOpt.isEmpty()) {
            return;
        }

        Optional<String> newAddressOpt = readLine("Enter new address (optional, press Enter to skip): ");
        if (newAddressOpt.isEmpty()) {
            return;
        }

        try {
            supplierService.updateSupplier(
                    code,
                    newNameOpt.get(),
                    newContactOpt.get().isBlank() ? null : newContactOpt.get(),
                    newPhoneOpt.get().isBlank() ? null : newPhoneOpt.get(),
                    newEmailOpt.get().isBlank() ? null : newEmailOpt.get(),
                    newAddressOpt.get().isBlank() ? null : newAddressOpt.get()
            );
            System.out.println("Supplier updated successfully.");
        } catch (ValidationException e) {
            System.out.println("Validation Error: " + e.getMessage());
        } catch (DatabaseException e) {
            System.out.println("Database Error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handleDeleteSupplier() {
        System.out.println("\n[ Delete Supplier ]");
        Optional<String> codeOpt = promptNonBlankString("Enter Supplier Code to delete: ", "Supplier code");
        if (codeOpt.isEmpty()) {
            return;
        }

        String code = codeOpt.get();
        Optional<Supplier> supplierOpt;
        try {
            supplierOpt = supplierService.findSupplierByCode(code);
        } catch (ValidationException e) {
            System.out.println("Validation Error: " + e.getMessage());
            return;
        } catch (DatabaseException e) {
            System.out.println("Database Error: " + e.getMessage());
            return;
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            return;
        }

        if (supplierOpt.isEmpty()) {
            System.out.println("Supplier not found with code: " + code);
            return;
        }

        Supplier s = supplierOpt.get();
        System.out.println("\nSupplier to Delete:");
        printSupplierDetails(s);

        while (true) {
            System.out.print("Are you sure you want to delete this supplier? (y/n): ");
            if (!scanner.hasNextLine()) {
                break;
            }
            String confirm = scanner.nextLine().trim();
            if (confirm.equalsIgnoreCase("y")) {
                try {
                    supplierService.deleteSupplierByCode(code);
                    System.out.println("Supplier deleted successfully.");
                } catch (ValidationException e) {
                    System.out.println("Validation Error: " + e.getMessage());
                } catch (DatabaseException e) {
                    System.out.println("Database Error: " + e.getMessage());
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                }
                break;
            } else if (confirm.equalsIgnoreCase("n")) {
                System.out.println("Delete cancelled.");
                break;
            } else {
                System.out.println("Please enter Y or N.");
            }
        }
    }

    private void printSupplierDetails(Supplier s) {
        System.out.println("-------------------------");
        System.out.println("ID: " + s.getId());
        System.out.println("Supplier Code: " + s.getSupplierCode());
        System.out.println("Name: " + s.getName());
        System.out.println("Contact Name: " + (s.getContactName() != null ? s.getContactName() : "-"));
        System.out.println("Phone: " + (s.getPhone() != null ? s.getPhone() : "-"));
        System.out.println("Email: " + (s.getEmail() != null ? s.getEmail() : "-"));
        System.out.println("Address: " + (s.getAddress() != null ? s.getAddress() : "-"));
        System.out.println("-------------------------");
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
}
