# StockFlow — Inventory & Warehouse Management System

A personal Java learning project focused on mastering hands-on Object-Oriented Programming (OOP), layered software architecture, and clean engineering practices.

---

## 🎯 Project Mission
StockFlow is a terminal-based Inventory and Warehouse Management System designed for small business operations:
- **Product Management:** Track items, categories, suppliers, and pricing.
- **Inventory Tracking:** Manage stock counts with threshold alerts (Low Stock).
- **Stock In / Stock Out:** Record physical inventory movements safely.
- **Audit Log / Transactions:** Complete history of every stock change (who, when, what, quantity).
- **User & Roles:** Role-Based Access Control (Admin vs. Staff).

---

## 🏗️ Architecture

StockFlow follows a clean layered architecture without bloated frameworks:

```
View / Console UI (TUI)
        ↓
    Controller
        ↓
     Service  (Business Logic & Validation)
        ↓
   DAO / Repository (JDBC & SQL Queries)
        ↓
   PostgreSQL Database
```

---

## 🛠️ Technology Stack
- **Language:** Java 21 (GraalVM JDK 21.0.7)
- **Build System:** Gradle (Groovy DSL)
- **Database:** PostgreSQL (Native JDBC)
- **Testing:** JUnit 5 (Jupiter)
- **Security:** Password hashing (jBCrypt - coming soon)

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
