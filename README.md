# E-commerce Backend — VietProDev Backend Challenge

Java Spring Boot backend for an e-commerce core system, built through 12 progressive challenges.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.3.5 |
| Database | PostgreSQL 16 |
| ORM | Spring Data JPA / Hibernate |
| Auth | JWT (jjwt 0.12.6) |
| Security | Spring Security 6 |
| API Docs | Swagger / OpenAPI (springdoc 2.6.0) |
| Payment | VNPay Sandbox |
| Build | Maven |
| Container | Docker / Docker Compose |

---

## Run Local

**Prerequisites:** Java 17, Maven, PostgreSQL running on port 5432

```bash
# 1. Create database
psql -U postgres -c "CREATE DATABASE test_db;"

# 2. Run init SQL
psql -U postgres -d test_db -f database/init.sql
psql -U postgres -d test_db -f database/sample_data.sql

# 3. Start app
mvn spring-boot:run
```

App runs at: http://localhost:8081
Swagger UI: http://localhost:8081/swagger-ui.html

---

## Run with Docker

**Prerequisites:** Docker & Docker Compose installed

```bash
# Build and start all services
docker-compose up --build

# Run in background
docker-compose up --build -d

# Stop
docker-compose down

# Stop and remove volumes (reset DB)
docker-compose down -v
```

App runs at: http://localhost:8081
Swagger UI: http://localhost:8081/swagger-ui.html
PostgreSQL: localhost:5433 (mapped from container 5432)

---

## Database Design

12 tables covering users, roles, products, inventory, cart, orders, payments and stock tracking.

```
users ──< user_roles >── roles
products >── categories
products ──< inventory
products ──< cart_items >── carts >── users
products ──< order_items >── orders >── users
orders ──< payments
products ──< stock_transactions
```

Key design decisions:
- `inventory.reserved_quantity` — soft hold when order is created
- `inventory.quantity` — actual stock, only decremented on payment success
- `order_items.price` — snapshot of product price at purchase time
- `inventory.version` — optimistic locking to prevent oversell

---

## API Overview

| Group | Endpoints |
|-------|-----------|
| Auth | POST /api/auth/register, /login, GET /profile |
| Category | CRUD /api/categories |
| Product | CRUD /api/products + search/filter/pagination |
| Inventory | POST /import, /export, GET /products/{id}, /transactions |
| Cart | POST /items, GET /, PUT /items/{id}, DELETE /items/{id}, /clear |
| Order | POST /, GET / (paginated), GET /{id} |
| Payment | POST /{id}/pay, /payment-fail, /cancel, /complete |
| VNPay | POST /api/payment/vnpay-create/{id}, GET /vnpay-return |

---

## Order Flow

```
[Customer] POST /api/orders
        ↓
Validate user → Validate products → Check inventory
        ↓
Reserve stock (reserved_quantity += qty)
        ↓
Create order (status: RESERVED) + order_items
        ↓
Clear cart
        ↓
[Customer] POST /api/orders/{id}/pay  OR  /api/payment/vnpay-create/{id}
        ↓
Payment SUCCESS → quantity -= qty, reserved_quantity -= qty → status: PAID
Payment FAIL    → reserved_quantity -= qty → status: PAYMENT_FAILED
        ↓
[Staff/Admin] POST /api/orders/{id}/complete → status: COMPLETED
```

---

## Transaction Handling

All order creation steps are wrapped in a single `@Transactional` method:

```
1. Validate user
2. Validate product (exists + active)
3. Check inventory (available >= requested)
4. Reserve stock
5. Create order
6. Create order items
7. Clear cart
```

Any `RuntimeException` at any step triggers a full rollback — no partial orders, no phantom stock reservations.

---

## Concurrency Handling (No Oversell)

**Strategy:** Optimistic Locking via `@Version` on `InventoryEntity`

When two requests try to reserve stock simultaneously:
- Both read `version = N`
- First request updates: `WHERE version = N` → success, version becomes `N+1`
- Second request updates: `WHERE version = N` → fails (version is now `N+1`)
- Spring throws `OptimisticLockingFailureException` → service retries once → returns 400 if still fails

**Result:** With stock=10 and 50 concurrent requests (qty=1 each), exactly ≤10 orders succeed.

---

## Role-Based Access Control

| Role | Permissions |
|------|-------------|
| ADMIN | Full access: manage products, categories, inventory, view all orders |
| STAFF | Manage products, manage inventory, manage orders |
| CUSTOMER | View products/categories, manage own cart, create and view own orders |

Default role on register: `CUSTOMER`

---

## Test Accounts

| Email | Password | Role |
|-------|----------|------|
| admin@vietprodev.local | password | ADMIN |
| staff@vietprodev.local | password | STAFF |
| customer1@vietprodev.local | password | CUSTOMER |
| customer2@vietprodev.local | password | CUSTOMER |