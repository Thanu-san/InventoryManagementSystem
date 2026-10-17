# StockFlow — Inventory & Warehouse Management System

A personal Java learning project focused on mastering hands-on Object-Oriented Programming (OOP), layered software architecture, clean engineering practices, and native JDBC database integration.

---

## 🎯 Project Mission & Status

StockFlow is a console-based Inventory and Warehouse Management System built incrementally to understand real-world Java development without magic frameworks.

### Current Implementation Status

#### ✅ Implemented
- **Product Domain & Business Logic:**
  - Rich domain model (`Product`) with immutable SKU identity, single-assignment ID, protected stock quantities, and guarded setters.
  - Centralized business rule validations in `ProductService` (non-blank SKU/Name, price $\ge 0$, quantity $\ge 0$, minimum stock $\ge 0$, unique SKU enforcement).
- **PostgreSQL Persistence via Native JDBC:**
  - `JdbcProductRepository` implementing `ProductRepository` using raw JDBC and parameterized SQL queries (`PreparedStatement`) to prevent SQL injection.
  - Auto-generated identity IDs retrieved via `Statement.RETURN_GENERATED_KEYS`.
  - Full CRUD operations: Create (Insert), Read (Find by SKU, List all ordered by ID), Update (editable fields only), and Delete by SKU.
  - External configuration via root `db.properties` (gitignored to protect secrets).
- **In-Memory Storage for Testing & Learning:**
  - `InMemoryProductRepository` implementing the same `ProductRepository` contract with insert-only `save()` and guarded `update()`, enabling fast isolated unit testing.
- **Console Interface:**
  - Interactive terminal menu (`ProductController` and `Main`) wired to PostgreSQL.
  - Clean error formatting for validation and user inputs.
- **Automated Testing:**
  - Comprehensive JUnit 5 test suite covering domain invariants, service business rules, in-memory repository contracts, database connection validation, and PostgreSQL integration tests.

#### 📋 Planned (Future Slices)
- **Inventory Operations:** Stock In and Stock Out movements with strict business checks.
- **Audit Logging:** Transaction records tracking stock movements (who, when, what, quantity).
- **Alerts & Reporting:** Low-stock threshold alerts and inventory summary metrics.
- **Categories & Suppliers:** Categorization and supplier tracking for products.
- **Security & RBAC:** User authentication, password hashing (jBCrypt), and role permissions (Admin vs. Staff).

---

## 🏗️ Layered Architecture

StockFlow strictly follows clean separation of concerns and the Dependency Inversion Principle:

```
Console UI / TUI (Main, ProductController)
        ↓
   Service Layer (ProductService - Business Logic & Validation)
        ↓
  Repository Layer (ProductRepository Interface)
        ↓
  Storage Implementations:
    ├── Active Runtime: JdbcProductRepository  → PostgreSQL (stockflow_db)
    └── Testing & Dev:  InMemoryProductRepository → In-Memory Map
```

High-level layers (`ProductController`, `ProductService`) depend strictly on the `ProductRepository` interface abstraction, completely decoupled from JDBC, SQL, or database drivers.

---

## 🛠️ Technology Stack
- **Language:** Java 21 (GraalVM JDK 21.0.7)
- **Build System:** Gradle 9 (Groovy DSL)
- **Testing:** JUnit 5 (JUnit BOM 5.10.2)
- **Database:** PostgreSQL 18
- **Driver:** PostgreSQL JDBC Driver (`org.postgresql:postgresql:42.7.13`)

---

## 🚀 Getting Started

### Prerequisites
- **JDK 21** (GraalVM 21 or Eclipse Adoptium OpenJDK 21)
- **PostgreSQL 18** installed and running locally
- **Git**

### Database Setup
1. Create a local PostgreSQL database named `stockflow_db`:
   ```sql
   CREATE DATABASE stockflow_db;
   ```
2. Run `schema.sql` to create the required table structure:
   ```bash
   psql -U postgres -d stockflow_db -f schema.sql
   ```

### Local Configuration (`db.properties`)
Create a local configuration file named `db.properties` in the project root:
```bash
cp db.properties.example db.properties
```

Edit `db.properties` with your local PostgreSQL credentials:
```properties
db.url=jdbc:postgresql://localhost:5432/stockflow_db
db.user=postgres
db.password=YOUR_LOCAL_PASSWORD
```

> [!IMPORTANT]
> The `db.properties` file is included in `.gitignore` and must never be committed to version control.

---

## 💻 Running the Application & Tests

### Build the Project
```bash
./gradlew build
```

### Run the Console Application
```bash
./gradlew run --console=plain
```

### Run Tests
StockFlow maintains two distinct types of tests:
1. **Unit Tests (Fast, No Database Required):**
   - `ProductTest`: Domain encapsulation and constraint validation.
   - `ProductServiceTest`: Business rules and validation flows using `InMemoryProductRepository`.
   - `InMemoryProductRepositoryTest`: In-memory repository contracts (insert-only save, guarded update).
2. **Integration Tests (Requires Live PostgreSQL):**
   - `DatabaseConnectionTest`: Verifies connectivity to `stockflow_db`.
   - `JdbcProductRepositoryTest`: Verifies real SQL execution, generated keys, row mapping, updates, and deletes against PostgreSQL with targeted test cleanup.

To run the entire test suite:
```bash
./gradlew test
```
