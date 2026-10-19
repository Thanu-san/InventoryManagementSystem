package com.stockflow.service;

import com.stockflow.exception.ValidationException;
import com.stockflow.model.InventorySummary;
import com.stockflow.model.InventoryTransaction;
import com.stockflow.model.Product;
import com.stockflow.model.TransactionType;
import com.stockflow.repository.InMemoryInventoryTransactionRepository;
import com.stockflow.repository.InMemoryProductRepository;
import com.stockflow.repository.InventoryTransactionRepository;
import com.stockflow.repository.JdbcInventoryTransactionRepository;
import com.stockflow.repository.JdbcProductRepository;
import com.stockflow.repository.ProductRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Service layer responsible for business logic, validation, and coordinating data storage.
 */
public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryTransactionRepository transactionRepository;

    public ProductService(ProductRepository productRepository) {
        this(productRepository, productRepository instanceof InMemoryProductRepository inMemoryRepo
                ? new InMemoryInventoryTransactionRepository(inMemoryRepo)
                : null);
    }

    public ProductService(ProductRepository productRepository, InventoryTransactionRepository transactionRepository) {
        validateRepositoryCompatibility(productRepository, transactionRepository);
        this.productRepository = productRepository;
        this.transactionRepository = transactionRepository;
    }

    private static void validateRepositoryCompatibility(ProductRepository productRepo,
                                                         InventoryTransactionRepository txRepo) {
        if (productRepo == null) {
            throw new IllegalArgumentException("Product repository cannot be null.");
        }
        if (txRepo == null) {
            return; // Permitted for Product CRUD-only service operations
        }
        if (productRepo instanceof JdbcProductRepository && txRepo instanceof InMemoryInventoryTransactionRepository) {
            throw new IllegalArgumentException(
                    "Unsupported repository combination: JdbcProductRepository cannot be paired with InMemoryInventoryTransactionRepository.");
        }
        if (productRepo instanceof InMemoryProductRepository && txRepo instanceof JdbcInventoryTransactionRepository) {
            throw new IllegalArgumentException(
                    "Unsupported repository combination: InMemoryProductRepository cannot be paired with JdbcInventoryTransactionRepository.");
        }
        if (productRepo instanceof InMemoryProductRepository && txRepo instanceof InMemoryInventoryTransactionRepository inMemoryTxRepo) {
            if (inMemoryTxRepo.getProductRepository() != productRepo) {
                throw new IllegalArgumentException(
                        "Mismatched repository instances: InMemoryInventoryTransactionRepository must be connected to the same InMemoryProductRepository instance used by ProductService.");
            }
        }
    }

    /**
     * Creates and saves a new product after validating all business rules.
     */
    public Product createProduct(String sku, String name, BigDecimal price, int quantity, int minimumStock) {
        validateProductData(sku, name, price, quantity, minimumStock);

        Product product = new Product(sku.trim(), name.trim(), price, quantity, minimumStock);
        return productRepository.save(product);
    }

    /**
     * Returns all products currently in the system.
     */
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    /**
     * Retrieves all products whose current quantity is less than or equal to their minimum stock.
     * Ordered by quantity ascending, then SKU ascending.
     *
     * @return list of low-stock products
     */
    public List<Product> getLowStockProducts() {
        return productRepository.findLowStockProducts();
    }

    /**
     * Calculates and returns aggregate inventory metrics:
     * total products, total units in stock, low-stock count, and out-of-stock count.
     *
     * @return summary snapshot of inventory
     */
    public InventorySummary getInventorySummary() {
        return productRepository.getInventorySummary();
    }

    /**
     * Finds a product by its SKU.
     * Validates that the input SKU is not blank and normalizes surrounding spaces.
     */
    public Optional<Product> findProductBySku(String sku) {
        if (sku == null || sku.trim().isEmpty()) {
            throw new ValidationException("Product SKU cannot be blank.");
        }
        return productRepository.findBySku(sku.trim());
    }

    /**
     * Updates editable fields (name, price, minimumStock) of an existing product.
     * Preserves ID, SKU, and quantity invariants.
     */
    public Product updateProduct(String sku, String name, BigDecimal price, int minimumStock) {
        if (sku == null || sku.trim().isEmpty()) {
            throw new ValidationException("Product SKU cannot be blank.");
        }

        Product product = productRepository.findBySku(sku.trim())
                .orElseThrow(() -> new ValidationException("Product not found with SKU: " + sku.trim()));

        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("Product name cannot be blank.");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Product price must be greater than or equal to 0.");
        }
        if (minimumStock < 0) {
            throw new ValidationException("Minimum stock must be greater than or equal to 0.");
        }

        product.setName(name);
        product.setPrice(price);
        product.setMinimumStock(minimumStock);

        boolean updated = productRepository.update(product);
        if (!updated) {
            throw new ValidationException("Product not found with SKU: " + sku.trim());
        }

        return product;
    }

    /**
     * Deletes a product by its SKU.
     * Validates that the SKU is not blank and performs a single delete operation through the repository.
     */
    public void deleteProductBySku(String sku) {
        if (sku == null || sku.trim().isEmpty()) {
            throw new ValidationException("Product SKU cannot be blank.");
        }

        Product product = productRepository.findBySku(sku.trim())
                .orElseThrow(() -> new ValidationException("Product not found with SKU: " + sku.trim()));

        if (transactionRepository != null) {
            List<InventoryTransaction> history = transactionRepository.findByProductId(product.getId());
            if (!history.isEmpty()) {
                throw new ValidationException(
                        "Cannot delete product '" + sku.trim() + "' because it has recorded inventory transaction history.");
            }
        }

        boolean deleted = productRepository.deleteBySku(sku.trim());
        if (!deleted) {
            throw new ValidationException("Product not found with SKU: " + sku.trim());
        }
    }

    /**
     * Increases product stock by the specified quantity and records an audit transaction.
     *
     * @param sku product SKU
     * @param quantity amount to add (must be > 0)
     * @return the created InventoryTransaction
     */
    public InventoryTransaction stockIn(String sku, int quantity) {
        ensureTransactionRepositoryConfigured();

        if (sku == null || sku.trim().isEmpty()) {
            throw new ValidationException("Product SKU cannot be blank.");
        }
        if (quantity <= 0) {
            throw new ValidationException("Stock In quantity must be greater than zero.");
        }

        Product product = productRepository.findBySku(sku.trim())
                .orElseThrow(() -> new ValidationException("Product not found with SKU: " + sku.trim()));

        if ((long) product.getQuantity() + quantity > Integer.MAX_VALUE) {
            throw new ValidationException("Stock In would cause quantity to exceed maximum capacity.");
        }

        return transactionRepository.recordMovement(product.getId(), TransactionType.STOCK_IN, quantity);
    }

    /**
     * Decreases product stock by the specified quantity and records an audit transaction.
     *
     * @param sku product SKU
     * @param quantity amount to remove (must be > 0)
     * @return the created InventoryTransaction
     */
    public InventoryTransaction stockOut(String sku, int quantity) {
        ensureTransactionRepositoryConfigured();

        if (sku == null || sku.trim().isEmpty()) {
            throw new ValidationException("Product SKU cannot be blank.");
        }
        if (quantity <= 0) {
            throw new ValidationException("Stock Out quantity must be greater than zero.");
        }

        Product product = productRepository.findBySku(sku.trim())
                .orElseThrow(() -> new ValidationException("Product not found with SKU: " + sku.trim()));

        if (product.getQuantity() < quantity) {
            throw new ValidationException("Insufficient stock for product '" + sku.trim() +
                    "'. Available: " + product.getQuantity() + ", Requested: " + quantity);
        }

        return transactionRepository.recordMovement(product.getId(), TransactionType.STOCK_OUT, quantity);
    }

    /**
     * Retrieves audit transaction history for a product by its SKU.
     *
     * @param sku product SKU
     * @return list of transactions ordered by createdAt DESC, id DESC
     */
    public List<InventoryTransaction> getTransactionHistory(String sku) {
        ensureTransactionRepositoryConfigured();

        if (sku == null || sku.trim().isEmpty()) {
            throw new ValidationException("Product SKU cannot be blank.");
        }

        Product product = productRepository.findBySku(sku.trim())
                .orElseThrow(() -> new ValidationException("Product not found with SKU: " + sku.trim()));

        return transactionRepository.findByProductId(product.getId());
    }

    private void ensureTransactionRepositoryConfigured() {
        if (transactionRepository == null) {
            throw new IllegalStateException(
                    "Stock movements are not supported: no compatible InventoryTransactionRepository is configured for this ProductService.");
        }
    }

    /**
     * Centralized validation of business rules for product creation.
     */
    private void validateProductData(String sku, String name, BigDecimal price, int quantity, int minimumStock) {
        // Rule 1: SKU must not be blank
        if (sku == null || sku.trim().isEmpty()) {
            throw new ValidationException("Product SKU cannot be blank.");
        }

        // Rule 2: SKU must be unique
        if (productRepository.existsBySku(sku.trim())) {
            throw new ValidationException("Product with SKU '" + sku.trim() + "' already exists.");
        }

        // Rule 3: Name must not be blank
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("Product name cannot be blank.");
        }

        // Rule 4: Price must be >= 0
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Product price must be greater than or equal to 0.");
        }

        // Rule 5: Quantity must be >= 0
        if (quantity < 0) {
            throw new ValidationException("Quantity must be greater than or equal to 0.");
        }

        // Rule 6: Minimum stock must be >= 0
        if (minimumStock < 0) {
            throw new ValidationException("Minimum stock must be greater than or equal to 0.");
        }
    }
}
