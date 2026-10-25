# Stock Reservation API

A REST API built with Java, Spring Boot, Spring Data JPA, and a persistent SQL database to manage product inventory and stock reservations.

The API supports creating reservations for multiple products, confirming reservations, cancelling active reservations, and maintaining strict stock consistency under high concurrency.

---

## Features
- **Retrieve product inventory**: View total, reserved, and available stock for all products.
- **Atomic multi-item reservations**: Create reservations containing one or more products with all-or-nothing stock holds.
- **Validation**: Strict validation for product IDs, positive quantities, and available stock.
- **Prevent overselling**: Database transactions and pessimistic locking (`SELECT ... FOR UPDATE`) prevent concurrent requests from overselling limited stock.
- **Deadlock prevention**: Ascending order lock acquisition across multi-item requests.
- **Confirm active reservations**: Deduct total and reserved stock to complete the purchase.
- **Cancel active reservations**: Release held stock back to available warehouse inventory.
- **Idempotency**: Repeating confirmation on confirmed reservations or cancellation on cancelled reservations does not modify inventory twice.
- **State machine enforcement**: Strict state lifecycle (`ACTIVE -> CONFIRMED`, `ACTIVE -> CANCELLED`). Disallows confirming cancelled reservations or cancelling confirmed reservations.
- **Persistent storage across restarts**: Live warehouse inventory and reservations survive application restarts.

---

## Technology Stack
- **Java**: Version 21 (or 17+)
- **Framework**: Spring Boot 3.3.4
- **Web**: Spring Web MVC
- **Persistence**: Spring Data JPA / Hibernate
- **Database**: 
  - **Default**: Relational SQL (File-backed H2 at `./data/inventorydb`) with full transactional ACID support (Zero external setup or installation required).
  - **Production/Optional**: MySQL 8 / PostgreSQL (via Docker Compose profile).
- **Build Tool**: Maven & Maven Wrapper (`mvnw`, `mvnw.cmd`)
- **Validation**: Jakarta Bean Validation (`@Valid`, `@NotNull`, `@Min`)
- **API Documentation**: OpenAPI / Swagger UI
- **Testing**: JUnit 5, Spring Boot Test, Multi-threaded Concurrency Execution

---

## Prerequisites
- Java 21 (or Java 17+)
- Git

Verify Java and Maven Wrapper availability:
```bash
java -version
./mvnw.cmd -version    # Windows
./mvnw -version        # macOS/Linux
```

---

## Database Setup

### Default Setup (File-backed SQL - Ready Out-of-the-Box)
By default, the application is pre-configured to use a persistent file-backed SQL database stored on disk at `./data/inventorydb.mv.db`. **No external database server installation or setup is required.** All stock and reservation mutations persist across application restarts.

### Optional Setup (MySQL 8)
If you prefer running against a local MySQL server:
1. Create the MySQL database:
   ```sql
   CREATE DATABASE stock_reservation;
   ```
2. Set environment variables or configure `application.properties`:
   ```properties
   spring.datasource.url=jdbc:mysql://localhost:3306/stock_reservation
   spring.datasource.username=root
   spring.datasource.password=your_password
   spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect
   ```

---

## Sample Inventory (Seed Data)
On initial startup, if the database is empty, the application automatically seeds the exact catalog specified in the assignment brief:

| ID | SKU | Product Name | Total Stock | Reserved Stock | Available Stock |
|---|---|---|---|---|---|
| 1 | `NOTE-001` | Notebook | 10 | 0 | 10 |
| 2 | `PEN-001` | Pen | 5 | 0 | 5 |
| 3 | `BAG-001` | Backpack | 2 | 0 | 2 |

*Note: Seed data runs conditionally (`count() == 0`) and never resets live stock when the application restarts.*

---

## Run the Application

From the project root:

```bash
# Windows
.\mvnw.cmd spring-boot:run

# macOS / Linux
./mvnw spring-boot:run
```

The application starts on port **`8080`**.

Interactive Swagger documentation is available at:
👉 **`http://localhost:8080/swagger-ui.html`**

Database Web Console is available at:
👉 **`http://localhost:8080/h2-console`** *(JDBC URL: `jdbc:h2:file:./data/inventorydb`, User: `sa`, Password: blank)*

---

## API Endpoints

### 1. List Products
`GET /api/products` (or `/api/inventory`)

Returns the products and their inventory information.

**Sample Response (`200 OK`):**
```json
[
  {
    "id": 1,
    "sku": "NOTE-001",
    "name": "Notebook",
    "totalStock": 10,
    "reservedStock": 0,
    "availableStock": 10
  },
  {
    "id": 2,
    "sku": "PEN-001",
    "name": "Pen",
    "totalStock": 5,
    "reservedStock": 0,
    "availableStock": 5
  },
  {
    "id": 3,
    "sku": "BAG-001",
    "name": "Backpack",
    "totalStock": 2,
    "reservedStock": 0,
    "availableStock": 2
  }
]
```

---

### 2. Create a Reservation
`POST /api/reservations`  
`Content-Type: application/json`

Example request (Reserve 3 Notebooks and 2 Pens):
```json
{
  "items": [
    {
      "productId": 1,
      "quantity": 3
    },
    {
      "productId": 2,
      "quantity": 2
    }
  ]
}
```

**Sample Response (`201 Created`):**
```json
{
  "id": 1,
  "status": "ACTIVE",
  "createdAt": "2026-10-25T17:30:00",
  "updatedAt": "2026-10-25T17:30:00",
  "items": [
    { "productId": 1, "sku": "NOTE-001", "productName": "Notebook", "quantity": 3 },
    { "productId": 2, "sku": "PEN-001", "productName": "Pen", "quantity": 2 }
  ]
}
```
*The product IDs must exist in the database, and the requested quantities must be positive and available. If any single item is short of stock, the entire request fails atomically.*

---

### 3. Retrieve Reservation by ID
`GET /api/reservations/{id}`

Returns the reservation identifier, current status, items, quantities, and audit timestamps.

---

### 4. Confirm a Reservation
`POST /api/reservations/{id}/confirm`

Confirms an active reservation: reduces `totalStock` and `reservedStock` by reserved quantities, completing the purchase. Repeating confirmation on an already confirmed reservation is idempotent and returns the existing state without modifying inventory twice.

**Sample Response (`200 OK`):**
```json
{
  "id": 1,
  "status": "CONFIRMED"
}
```

---

### 5. Cancel a Reservation
`POST /api/reservations/{id}/cancel`

Cancels an active reservation: releases reserved stock back to available warehouse inventory. Repeating cancellation is idempotent and does not release stock a second time.

**Sample Response (`200 OK`):**
```json
{
  "id": 1,
  "status": "CANCELLED"
}
```

---

## Error Handling

The API validates request data, product availability, and lifecycle state rules using standard HTTP status codes:

- **`400 Bad Request`**: Validation failures (empty items list, negative or zero quantities).
- **`404 Not Found`**: Non-existent product ID or reservation ID.
- **`409 Conflict`**: Insufficient stock for one or more requested items (Atomic failure).
- **`409 Conflict`**: Invalid state transitions (attempting to confirm a cancelled reservation or cancel a confirmed reservation).

Example error response:
```json
{
  "timestamp": "2026-10-25T17:32:00",
  "status": 409,
  "error": "Conflict",
  "message": "Insufficient available stock for SKU 'BAG-001' (Backpack). Available: 2, Requested: 3",
  "path": "/api/reservations"
}
```

---

## Run Tests

Run the full automated test suite:
```bash
# Windows
.\mvnw.cmd test

# macOS / Linux
./mvnw test
```

Build and package the project:
```bash
# Windows
.\mvnw.cmd clean package

# macOS / Linux
./mvnw clean package
```

### Test Coverage Highlights:
- **`testSuccessfulLifecycle`**: Creates, retrieves, and confirms multi-product reservations.
- **`testCancellationLifecycle`**: Cancels reservations and verifies stock release.
- **`testAtomicFailure`**: Confirms that one unavailable product aborts the entire transaction with zero inventory changes.
- **`testInvalidRequestsAndTransitions`**: Exercises invalid states, missing IDs, and validation rules.
- **`testIdempotency`**: Verifies repeated confirm/cancel calls never double-modify inventory.
- **`testConcurrencyCompetingRequests`**: Multi-threaded test with simultaneous threads competing for limited backpack stock; verifies pessimistic locking prevents overselling.

---

## Example Demo Sequence (cURL)

1. **View inventory**:
   ```bash
   curl -X GET http://localhost:8080/api/products
   ```
2. **Create reservation (3 Notebooks, 2 Pens)**:
   ```bash
   curl -X POST http://localhost:8080/api/reservations \
     -H "Content-Type: application/json" \
     -d '{"items":[{"productId":1,"quantity":3},{"productId":2,"quantity":2}]}'
   ```
3. **Verify hold**:
   ```bash
   curl -X GET http://localhost:8080/api/products
   ```
4. **Confirm reservation**:
   ```bash
   curl -X POST http://localhost:8080/api/reservations/1/confirm
   ```
5. **Verify final sale**:
   ```bash
   curl -X GET http://localhost:8080/api/products
   ```
6. **Trigger atomic failure (Request 3 backpacks when only 2 exist)**:
   ```bash
   curl -X POST http://localhost:8080/api/reservations \
     -H "Content-Type: application/json" \
     -d '{"items":[{"productId":3,"quantity":3}]}'
   ```

---

## Project Structure

```
stock-reservation-api/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/analyticalrx/inventory/
│   │   │       ├── config/              # DataLoader (seed data initialization)
│   │   │       ├── controller/          # REST Controllers
│   │   │       ├── dto/                 # Request & Response DTOs
│   │   │       ├── exception/           # Custom Exceptions & GlobalExceptionHandler
│   │   │       ├── model/               # JPA Entities (Product, Reservation, etc.)
│   │   │       ├── repository/          # Spring Data JPA Repositories (Pessimistic Locking)
│   │   │       ├── service/             # InventoryService (Business & Concurrency Logic)
│   │   │       └── StockReservationApiApplication.java
│   │   └── resources/
│   │       ├── application.properties
│   │       └── application-postgres.properties
│   └── test/
│       └── java/
│           └── com/analyticalrx/inventory/
│               └── StockReservationApiIntegrationTest.java
├── .mvn/
├── .gitignore
├── pom.xml
├── mvnw
├── mvnw.cmd
├── README.md
├── PLAN.md
├── AI_USAGE.md
└── docker-compose.yml
```

---

## Limitations and Future Improvements
- **Automatic Expiration**: Implement a background scheduler (or Redis TTL / RabbitMQ delayed exchange) to automatically expire and release reservations abandoned after a designated timeout.
- **Distributed Locking**: For multi-node microservices deployments, transition from database row-level locking to distributed locks (e.g., Redis Redlock) or an event-sourced inventory ledger.
- **Idempotency Keys**: Add client-supplied `Idempotency-Key` HTTP headers to safeguard against network timeouts during initial reservation creation.
