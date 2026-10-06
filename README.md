# StockFlow — Inventory & Warehouse Management System

A personal Java learning project focused on mastering hands-on Object-Oriented Programming (OOP), layered software architecture, and clean engineering practices.

---

## 🎯 Project Mission & Status

StockFlow is a console-based Inventory and Warehouse Management System built incrementally to understand real-world Java development.

### Feature Status

#### ✅ Implemented (Vertical Slice #1)
- **Product Management:**
  - Create new product with domain encapsulation (`Product`).
  - Business rule validations in `ProductService` (non-blank SKU/Name, price $\ge 0$, quantity $\ge 0$, min stock $\ge 0$, unique SKU).
  - List products in a readable console table format.
- **In-Memory Storage:**
  - `ProductRepository` interface decoupling business logic from persistence.
  - `InMemoryProductRepository` using `Map<String, Product>` (keyed by SKU).
- **Console Interface:**
  - Interactive terminal menu (`ProductController` and `Main`).
  - Targeted exception handling for user input and business validation errors.
- **Testing:**
  - Automated test suite using JUnit 5 (domain encapsulation and service validation rules).

#### 🚧 In Progress
- Domain integrity and architecture refinement.

#### 📋 Planned (Future Slices)
- **Database Persistence:** PostgreSQL integration via native JDBC (`JdbcProductRepository`).
- **Inventory Operations:** Stock In and Stock Out movements with strict business checks.
- **Audit Logging:** Transaction records tracking who, when, what, and quantity.
- **Alerts & Reporting:** Low-stock threshold alerts and summary metrics.
- **Categories & Suppliers:** Categorization and supplier tracking for products.
- **Security & RBAC:** User authentication, password hashing (jBCrypt), and role permissions (Admin vs. Staff).

---

## 🏗️ Architecture

StockFlow follows a clean layered architecture:

```
Console UI / TUI (Main, ProductController)
        ↓
   Service Layer (ProductService - Business Logic & Validation)
        ↓
  Repository Layer (ProductRepository Interface)
        ↓
  Storage Implementation (Current: InMemoryProductRepository | Planned: PostgreSQL JDBC)
```

---

## 🛠️ Technology Stack
- **Language:** Java 21 (GraalVM JDK 21.0.7)
- **Build System:** Gradle (Groovy DSL)
- **Testing:** JUnit 5 (JUnit BOM 5.10.2)
- **Database (Planned):** PostgreSQL via Native JDBC

---

## 🚀 Getting Started

### Prerequisites
- JDK 21 (GraalVM 21 recommended)
- Git

### Build the Project
```bash
./gradlew build
```

### Run Tests
```bash
./gradlew test
```

### Run the Console Application
```bash
./gradlew run --console=plain
```
