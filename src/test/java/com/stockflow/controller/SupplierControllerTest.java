package com.stockflow.controller;

import com.stockflow.model.Supplier;
import com.stockflow.repository.InMemorySupplierRepository;
import com.stockflow.service.SupplierService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SupplierController Console Tests")
class SupplierControllerTest {

    private InMemorySupplierRepository repository;
    private SupplierService service;

    @BeforeEach
    void setUp() {
        repository = new InMemorySupplierRepository();
        service = new SupplierService(repository);
    }

    private SupplierController createController(String input) {
        Scanner scanner = new Scanner(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
        return new SupplierController(service, scanner);
    }

    private String captureOutput(Runnable action) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(baos, true, StandardCharsets.UTF_8));
        try {
            action.run();
        } finally {
            System.setOut(originalOut);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }

    // --- Add Supplier ---

    @Test
    @DisplayName("Should add supplier with all fields successfully")
    void shouldAddSupplierWithAllFields() {
        String input = "1\nSUP-001\nAcme Corp\nJohn Doe\n555-1234\njohn@acme.com\n123 Main St\n6\n";
        SupplierController controller = createController(input);

        String output = captureOutput(controller::runMenu);

        assertTrue(output.contains("Success: Supplier created successfully!"));
        assertTrue(service.findSupplierByCode("SUP-001").isPresent());
    }

    @Test
    @DisplayName("Should add supplier with optional fields skipped")
    void shouldAddSupplierWithOptionalFieldsSkipped() {
        String input = "1\nSUP-002\nBeta Inc\n\n\n\n\n6\n";
        SupplierController controller = createController(input);

        String output = captureOutput(controller::runMenu);

        assertTrue(output.contains("Success: Supplier created successfully!"));
        Supplier supplier = service.findSupplierByCode("SUP-002").orElseThrow();
        assertEquals("Beta Inc", supplier.getName());
        assertNull(supplier.getContactName());
        assertNull(supplier.getPhone());
        assertNull(supplier.getEmail());
        assertNull(supplier.getAddress());
    }

    @Test
    @DisplayName("Should show validation error for duplicate supplier code")
    void shouldShowValidationErrorForDuplicateCode() {
        service.createSupplier("SUP-DUP", "First", null, null, null, null);

        String input = "1\nSUP-DUP\nSecond\n\n\n\n\n6\n";
        SupplierController controller = createController(input);

        String output = captureOutput(controller::runMenu);

        assertTrue(output.contains("Validation Error:"));
        assertTrue(output.contains("already exists"));
    }

    @Test
    @DisplayName("Should retry when blank supplier code entered")
    void shouldRetryWhenBlankCodeEntered() {
        String input = "1\n\n\nSUP-RETRY\nRetry Name\n\n\n\n\n6\n";
        SupplierController controller = createController(input);

        String output = captureOutput(controller::runMenu);

        assertTrue(output.contains("cannot be blank"));
        assertTrue(output.contains("Success: Supplier created successfully!"));
    }

    // --- Find Supplier ---

    @Test
    @DisplayName("Should find and display existing supplier")
    void shouldFindAndDisplayExistingSupplier() {
        service.createSupplier("SUP-SHOW", "Show Me", "Contact", "555", "a@b.com", "Addr");

        String input = "2\nSUP-SHOW\n6\n";
        SupplierController controller = createController(input);

        String output = captureOutput(controller::runMenu);

        assertTrue(output.contains("SUP-SHOW"));
        assertTrue(output.contains("Show Me"));
        assertTrue(output.contains("Contact"));
    }

    @Test
    @DisplayName("Should show message when supplier not found")
    void shouldShowMessageWhenSupplierNotFound() {
        String input = "2\nNON-EXIST\n6\n";
        SupplierController controller = createController(input);

        String output = captureOutput(controller::runMenu);

        assertTrue(output.contains("Supplier not found with code: NON-EXIST"));
    }

    // --- List Suppliers ---

    @Test
    @DisplayName("Should list all suppliers in table format")
    void shouldListAllSuppliersInTableFormat() {
        service.createSupplier("SUP-L1", "Alpha Co", null, null, null, null);
        service.createSupplier("SUP-L2", "Beta Inc", "Contact B", "555", null, null);

        String input = "3\n6\n";
        SupplierController controller = createController(input);

        String output = captureOutput(controller::runMenu);

        assertTrue(output.contains("SUP-L1"));
        assertTrue(output.contains("Alpha Co"));
        assertTrue(output.contains("SUP-L2"));
        assertTrue(output.contains("Beta Inc"));
    }

    @Test
    @DisplayName("Should show message when no suppliers exist")
    void shouldShowMessageWhenNoSuppliersExist() {
        String input = "3\n6\n";
        SupplierController controller = createController(input);

        String output = captureOutput(controller::runMenu);

        assertTrue(output.contains("No suppliers found"));
    }

    // --- Update Supplier ---

    @Test
    @DisplayName("Should update existing supplier successfully")
    void shouldUpdateExistingSupplierSuccessfully() {
        service.createSupplier("SUP-UPD", "Old Name", "Old Contact",
                "111", "old@test.com", "Old Addr");

        String input = "4\nSUP-UPD\nNew Name\nNew Contact\n222\nnew@test.com\nNew Addr\n6\n";
        SupplierController controller = createController(input);

        String output = captureOutput(controller::runMenu);

        assertTrue(output.contains("Supplier updated successfully"));

        Supplier updated = service.findSupplierByCode("SUP-UPD").orElseThrow();
        assertEquals("New Name", updated.getName());
        assertEquals("New Contact", updated.getContactName());
    }

    @Test
    @DisplayName("Should show message when updating non-existent supplier")
    void shouldShowMessageWhenUpdatingNonExistent() {
        String input = "4\nNON-EXIST\n6\n";
        SupplierController controller = createController(input);

        String output = captureOutput(controller::runMenu);

        assertTrue(output.contains("Supplier not found with code: NON-EXIST"));
    }

    // --- Delete Supplier ---

    @Test
    @DisplayName("Should delete supplier after confirmation")
    void shouldDeleteSupplierAfterConfirmation() {
        service.createSupplier("SUP-DEL", "Delete Me", null, null, null, null);

        String input = "5\nSUP-DEL\ny\n6\n";
        SupplierController controller = createController(input);

        String output = captureOutput(controller::runMenu);

        assertTrue(output.contains("Supplier deleted successfully"));
        assertTrue(service.findSupplierByCode("SUP-DEL").isEmpty());
    }

    @Test
    @DisplayName("Should cancel deletion when user enters N")
    void shouldCancelDeletionWhenUserEntersN() {
        service.createSupplier("SUP-KEEP", "Keep Me", null, null, null, null);

        String input = "5\nSUP-KEEP\nn\n6\n";
        SupplierController controller = createController(input);

        String output = captureOutput(controller::runMenu);

        assertTrue(output.contains("Delete cancelled"));
        assertTrue(service.findSupplierByCode("SUP-KEEP").isPresent());
    }

    @Test
    @DisplayName("Should retry confirmation on invalid input then accept Y")
    void shouldRetryConfirmationOnInvalidInput() {
        service.createSupplier("SUP-CONF", "Confirm Test", null, null, null, null);

        String input = "5\nSUP-CONF\nmaybe\nx\ny\n6\n";
        SupplierController controller = createController(input);

        String output = captureOutput(controller::runMenu);

        assertTrue(output.contains("Please enter Y or N"));
        assertTrue(output.contains("Supplier deleted successfully"));
    }

    @Test
    @DisplayName("Should show message when deleting non-existent supplier")
    void shouldShowMessageWhenDeletingNonExistent() {
        String input = "5\nNON-EXIST\n6\n";
        SupplierController controller = createController(input);

        String output = captureOutput(controller::runMenu);

        assertTrue(output.contains("Supplier not found with code: NON-EXIST"));
    }

    // --- Menu Navigation ---

    @Test
    @DisplayName("Should show invalid option message for unknown menu choice")
    void shouldShowInvalidOptionForUnknownChoice() {
        String input = "99\n6\n";
        SupplierController controller = createController(input);

        String output = captureOutput(controller::runMenu);

        assertTrue(output.contains("Invalid option"));
    }

    @Test
    @DisplayName("Should exit menu when option 6 is selected")
    void shouldExitMenuWhenOption6Selected() {
        String input = "6\n";
        SupplierController controller = createController(input);

        String output = captureOutput(controller::runMenu);

        assertTrue(output.contains("Supplier Management"));
    }

    @Test
    @DisplayName("Should handle end-of-input gracefully")
    void shouldHandleEndOfInputGracefully() {
        String input = "";
        SupplierController controller = createController(input);

        // Should not throw
        assertDoesNotThrow(() -> captureOutput(controller::runMenu));
    }
}
