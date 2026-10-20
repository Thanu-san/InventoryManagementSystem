package com.stockflow.service;

import com.stockflow.exception.ValidationException;
import com.stockflow.model.InventorySummary;
import com.stockflow.model.InventoryTransaction;
import com.stockflow.model.InventoryValuationItem;
import com.stockflow.model.InventoryValuationReport;
import com.stockflow.model.PagedResult;
import com.stockflow.model.Product;
import com.stockflow.model.SortDirection;
import com.stockflow.model.SortField;
import com.stockflow.model.Supplier;
import com.stockflow.model.TransactionType;
import com.stockflow.repository.InMemoryInventoryTransactionRepository;
import com.stockflow.repository.InMemoryProductRepository;
import com.stockflow.repository.InMemorySupplierRepository;
import com.stockflow.repository.InventoryTransactionRepository;
import com.stockflow.repository.JdbcInventoryTransactionRepository;
import com.stockflow.repository.JdbcProductRepository;
import com.stockflow.repository.JdbcSupplierRepository;
import com.stockflow.repository.ProductRepository;
import com.stockflow.repository.PurchaseOrderRepository;
import com.stockflow.repository.SupplierRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service layer responsible for business logic, validation, and coordinating data storage.
 */
public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final SupplierRepository supplierRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    public ProductService(ProductRepository productRepository) {
        this(productRepository, productRepository instanceof InMemoryProductRepository inMemoryRepo
                ? new InMemoryInventoryTransactionRepository(inMemoryRepo)
                : null,
             null,
             null);
    }

    public ProductService(ProductRepository productRepository, InventoryTransactionRepository transactionRepository) {
        this(productRepository, transactionRepository, null, null);
    }

    public ProductService(ProductRepository productRepository,
                          InventoryTransactionRepository transactionRepository,
                          SupplierRepository supplierRepository) {
        this(productRepository, transactionRepository, supplierRepository, null);
    }

    public ProductService(ProductRepository productRepository,
                          InventoryTransactionRepository transactionRepository,
                          SupplierRepository supplierRepository,
                          PurchaseOrderRepository purchaseOrderRepository) {
        validateRepositoryCompatibility(productRepository, transactionRepository, supplierRepository);
        this.productRepository = productRepository;
        this.transactionRepository = transactionRepository;
        this.supplierRepository = supplierRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
    }

    private static void validateRepositoryCompatibility(ProductRepository productRepo,
                                                         InventoryTransactionRepository txRepo,
                                                         SupplierRepository supplierRepo) {
        if (productRepo == null) {
            throw new IllegalArgumentException("Product repository cannot be null.");
        }
        if (txRepo != null) {
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
        if (supplierRepo != null) {
            if (productRepo instanceof JdbcProductRepository && supplierRepo instanceof InMemorySupplierRepository) {
                throw new IllegalArgumentException(
                        "Unsupported repository combination: JdbcProductRepository cannot be paired with InMemorySupplierRepository.");
            }
            if (productRepo instanceof InMemoryProductRepository && supplierRepo instanceof JdbcSupplierRepository) {
                throw new IllegalArgumentException(
                        "Unsupported repository combination: InMemoryProductRepository cannot be paired with JdbcSupplierRepository.");
            }
        }
    }

    /**
     * Creates and saves a new product after validating all business rules.
     */
    public Product createProduct(String sku, String name, BigDecimal price, int quantity, int minimumStock) {
        return createProduct(sku, name, price, quantity, minimumStock, null);
    }

    /**
     * Creates and saves a new product with an optional supplier code after validating all business rules.
     */
    public Product createProduct(String sku, String name, BigDecimal price, int quantity, int minimumStock, String supplierCode) {
        validateProductData(sku, name, price, quantity, minimumStock);

        Long supplierId = null;
        if (supplierCode != null && !supplierCode.trim().isEmpty()) {
            ensureSupplierRepositoryConfigured();
            Supplier supplier = supplierRepository.findBySupplierCode(supplierCode.trim())
                    .orElseThrow(() -> new ValidationException("Supplier not found with code: " + supplierCode.trim()));
            supplierId = supplier.getId();
        }

        Product product = new Product(sku.trim(), name.trim(), price, quantity, minimumStock, supplierId);
        return productRepository.save(product);
    }

    /**
     * Returns all products currently in the system.
     */
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    /**
     * Retrieves a paginated and sorted slice of products.
     * Validates that page is >= 1, size is >= 1, and sort parameters are not null.
     *
     * @param page 1-based page number
     * @param size page size (items per page)
     * @param sortField field to sort by
     * @param sortDirection direction of sort (ASC or DESC)
     * @return paged result containing matching products and pagination metadata
     * @throws ValidationException if page < 1, size < 1, or sort parameters are null
     */
    public PagedResult<Product> getProductsPaged(int page, int size, SortField sortField, SortDirection sortDirection) {
        if (page < 1) {
            throw new ValidationException("Page number must be greater than or equal to 1.");
        }
        if (size < 1) {
            throw new ValidationException("Page size must be greater than or equal to 1.");
        }
        if (sortField == null) {
            throw new ValidationException("Sort field cannot be null.");
        }
        if (sortDirection == null) {
            throw new ValidationException("Sort direction cannot be null.");
        }

        return productRepository.findAll(page, size, sortField, sortDirection);
    }

    /**
     * Returns the total count of products in the repository.
     *
     * @return total product count
     */
    public long getProductCount() {
        return productRepository.count();
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
     * Generates an Inventory Valuation Report calculating valuation for each product
     * and total inventory value across the entire catalog.
     *
     * @return inventory valuation report containing individual product valuations and aggregate totals
     */
    public InventoryValuationReport getInventoryValuationReport() {
        List<Product> products = productRepository.findAll();
        if (products.isEmpty()) {
            return InventoryValuationReport.empty();
        }

        List<InventoryValuationItem> items = new ArrayList<>(products.size());
        long totalQuantity = 0L;
        BigDecimal totalValue = BigDecimal.ZERO;

        for (Product product : products) {
            int quantity = product.getQuantity();
            BigDecimal inventoryValue = product.getPrice().multiply(BigDecimal.valueOf(quantity));

            items.add(new InventoryValuationItem(
                    product.getSku(),
                    product.getName(),
                    product.getPrice(),
                    quantity,
                    inventoryValue
            ));

            totalQuantity += quantity;
            totalValue = totalValue.add(inventoryValue);
        }

        return new InventoryValuationReport(items, totalQuantity, totalValue);
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
     * Searches for products by a partial product name (case-insensitive).
     * Validates that the input search term is not blank and normalizes surrounding spaces.
     *
     * @param nameQuery the partial or full product name to search for
     * @return list of matching products ordered by SKU ascending
     * @throws ValidationException if the search term is null or blank
     */
    public List<Product> searchProductsByName(String nameQuery) {
        if (nameQuery == null || nameQuery.trim().isEmpty()) {
            throw new ValidationException("Search term cannot be blank.");
        }
        return productRepository.searchByName(nameQuery.trim());
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

        if (purchaseOrderRepository != null && purchaseOrderRepository.existsByProductId(product.getId())) {
            throw new ValidationException(
                    "Cannot delete product '" + sku.trim() + "' because it is referenced by one or more purchase order line items.");
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

    /**
     * Assigns a supplier to a product identified by SKU.
     *
     * @param sku product SKU
     * @param supplierCode supplier code to assign
     * @return the updated Product
     */
    public Product assignSupplier(String sku, String supplierCode) {
        ensureSupplierRepositoryConfigured();

        if (sku == null || sku.trim().isEmpty()) {
            throw new ValidationException("Product SKU cannot be blank.");
        }
        if (supplierCode == null || supplierCode.trim().isEmpty()) {
            throw new ValidationException("Supplier code cannot be blank.");
        }

        Product product = productRepository.findBySku(sku.trim())
                .orElseThrow(() -> new ValidationException("Product not found with SKU: " + sku.trim()));

        Supplier supplier = supplierRepository.findBySupplierCode(supplierCode.trim())
                .orElseThrow(() -> new ValidationException("Supplier not found with code: " + supplierCode.trim()));

        boolean updated = productRepository.updateSupplier(product.getSku(), supplier.getId());
        if (!updated) {
            throw new ValidationException("Product not found with SKU: " + sku.trim());
        }

        product.setSupplierId(supplier.getId());
        return product;
    }

    /**
     * Removes the assigned supplier from a product identified by SKU.
     *
     * @param sku product SKU
     * @return the updated Product
     */
    public Product removeSupplier(String sku) {
        if (sku == null || sku.trim().isEmpty()) {
            throw new ValidationException("Product SKU cannot be blank.");
        }

        Product product = productRepository.findBySku(sku.trim())
                .orElseThrow(() -> new ValidationException("Product not found with SKU: " + sku.trim()));

        boolean updated = productRepository.updateSupplier(product.getSku(), null);
        if (!updated) {
            throw new ValidationException("Product not found with SKU: " + sku.trim());
        }

        product.setSupplierId(null);
        return product;
    }

    /**
     * Retrieves the assigned supplier for a product identified by SKU.
     *
     * @param sku product SKU
     * @return Optional containing the assigned Supplier, or empty if no supplier is assigned
     */
    public Optional<Supplier> getSupplierForProduct(String sku) {
        if (sku == null || sku.trim().isEmpty()) {
            throw new ValidationException("Product SKU cannot be blank.");
        }

        Product product = productRepository.findBySku(sku.trim())
                .orElseThrow(() -> new ValidationException("Product not found with SKU: " + sku.trim()));

        if (product.getSupplierId() == null) {
            return Optional.empty();
        }

        if (supplierRepository == null) {
            return Optional.empty();
        }

        return supplierRepository.findById(product.getSupplierId());
    }

    private void ensureSupplierRepositoryConfigured() {
        if (supplierRepository == null) {
            throw new IllegalStateException(
                    "Supplier operations are not supported: no SupplierRepository is configured for this ProductService.");
        }
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
