# Stock Reservation API

> High-concurrency warehouse inventory reservation service built for the AnalyticalRx Java Software Engineering Take-Home Assignment.

---

## 1. Prerequisites & Technology Stack

- **Java JDK**: Version 21 (or 17+)
- **Build Tool**: Apache Maven (Wrapper `mvnw` / `mvnw.cmd` included)
- **Framework**: Spring Boot 3.3.4
- **Persistence / Database**:
  - **Default**: Relational SQL (File-backed H2 at `./data/inventorydb`) with full ACID transaction support and persistent storage on disk across restarts. **Zero external installation or Docker required**.
  - **Optional**: PostgreSQL 16 (via Docker Compose profile).
- **Interactive Documentation**: Swagger / OpenAPI UI at `http://localhost:8080/swagger-ui.html`.

---

## 2. Inventory Model & Business Rules

$$\text{Available Stock} = \text{Total Stock} - \text{Reserved Stock}$$

- **Total Stock**: Units physically held in the warehouse.
- **Reserved Stock**: Units held by pending active reservations.
- **Available Stock**: Units available for new reservations.
- **Rules**:
  1. All quantities are non-negative whole numbers.
  2. All-or-nothing: Multi-item reservations either succeed entirely or roll back completely with zero stock modified.
  3. Strict state machine: Only `ACTIVE -> CONFIRMED` and `ACTIVE -> CANCELLED` are valid transitions.
  4. Concurrency safety: Pessimistic row-level locking (`SELECT ... FOR UPDATE`) prevents overselling under simultaneous requests.
  5. Deadlock prevention: Product IDs are sorted in ascending order before acquiring database row locks.

---

## 3. Quick Start & Build Instructions

### Compile & Run Tests
```bash
# Windows
mvnw.cmd clean test

# Linux / macOS
./mvnw clean test
```

### Run the Application Locally
```bash
# Windows
mvnw.cmd spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```
The server starts on port **`8080`**.

### Optional: Run with PostgreSQL via Docker
```bash
docker compose up -d
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=postgres
```

---

## 4. Seed Data & Database Reset

On initial startup, if the database is empty, the application automatically seeds the exact catalog required by the assignment:

| ID | SKU | Product Name | Total Stock | Reserved Stock | Available Stock |
|---|---|---|---|---|---|
| 1 | `NOTE-001` | Notebook | 10 | 0 | 10 |
| 2 | `PEN-001` | Pen | 5 | 0 | 5 |
| 3 | `BAG-001` | Backpack | 2 | 0 | 2 |

### Persistence Across Restarts
Data is stored in `./data/inventorydb.mv.db`. When the server is stopped and restarted, all current stock values and created reservations remain intact. Seed data only populates if `productRepository.count() == 0`.

### How to Reset Sample Data
To reset back to the initial seed state, stop the application and delete the `./data` folder:
```bash
# Windows PowerShell
Remove-Item -Recurse -Force ./data

# Linux / macOS
rm -rf ./data
```

---

## 5. Endpoints & API Contract

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/products` or `/api/inventory` | List all inventory items |
| `POST` | `/api/reservations` | Create a multi-item reservation |
| `GET` | `/api/reservations/{id}` | Get reservation details & status |
| `POST` | `/api/reservations/{id}/confirm` | Confirm reservation (deducts stock) |
| `POST` | `/api/reservations/{id}/cancel` | Cancel reservation (releases hold) |

---

## 6. Reproducible Step-by-Step Demo Sequence (cURL)

Execute these commands in any terminal (PowerShell, Command Prompt, or Bash) to verify all scenarios from the assignment brief.

### Step 1: View Initial Inventory
```bash
curl -X GET http://localhost:8080/api/products
```
**Expected Response:**
```json
[
  {"id":1,"sku":"NOTE-001","name":"Notebook","totalStock":10,"reservedStock":0,"availableStock":10},
  {"id":2,"sku":"PEN-001","name":"Pen","totalStock":5,"reservedStock":0,"availableStock":5},
  {"id":3,"sku":"BAG-001","name":"Backpack","totalStock":2,"reservedStock":0,"availableStock":2}
]
```

---

### Step 2: Example A — Reserve 3 Notebooks & 2 Pens
```bash
curl -X POST http://localhost:8080/api/reservations \
  -H "Content-Type: application/json" \
  -d '{
    "items": [
      {"productId": 1, "quantity": 3},
      {"productId": 2, "quantity": 2}
    ]
  }'
```
**Expected Response (201 Created):**
```json
{
  "id": 1,
  "status": "ACTIVE",
  "createdAt": "2026-10-25T16:50:00",
  "updatedAt": "2026-10-25T16:50:00",
  "items": [
    {"productId": 1, "sku": "NOTE-001", "productName": "Notebook", "quantity": 3},
    {"productId": 2, "sku": "PEN-001", "productName": "Pen", "quantity": 2}
  ]
}
```

Check stock (`NOTE-001` is now `10 / 3 / 7`, `PEN-001` is `5 / 2 / 3`):
```bash
curl -X GET http://localhost:8080/api/products
```

---

### Step 3: Example A (cont.) — Confirm Reservation
```bash
curl -X POST http://localhost:8080/api/reservations/1/confirm
```
**Expected Response (200 OK):**
```json
{
  "id": 1,
  "status": "CONFIRMED"
}
```
Check stock (`NOTE-001` is now `7 / 0 / 7`, `PEN-001` is `3 / 0 / 3`):
```bash
curl -X GET http://localhost:8080/api/products
```

---

### Step 4: Repeating Confirmation (Idempotent Test)
```bash
curl -X POST http://localhost:8080/api/reservations/1/confirm
```
**Result**: Returns the same `CONFIRMED` response without changing inventory counts again.

---

### Step 5: Example C — Atomic Failure (One Unavailable Item)
Request 2 notebooks and 3 backpacks (only 2 backpacks are in stock):
```bash
curl -X POST http://localhost:8080/api/reservations \
  -H "Content-Type: application/json" \
  -d '{
    "items": [
      {"productId": 1, "quantity": 2},
      {"productId": 3, "quantity": 3}
    ]
  }'
```
**Expected Response (409 Conflict):**
```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Insufficient available stock for SKU 'BAG-001' (Backpack). Available: 2, Requested: 3",
  "path": "/api/reservations"
}
```
Verify that notebook inventory was **not** held and backpack inventory was **not** changed:
```bash
curl -X GET http://localhost:8080/api/products
```

---

### Step 6: Example B — Reserve then Cancel
Create reservation for 1 notebook:
```bash
curl -X POST http://localhost:8080/api/reservations \
  -H "Content-Type: application/json" \
  -d '{
    "items": [
      {"productId": 1, "quantity": 1}
    ]
  }'
```
Cancel it:
```bash
curl -X POST http://localhost:8080/api/reservations/2/cancel
```
**Expected Response:** Status is `CANCELLED`, and reserved stock returns to `0`.

---

## 7. Concurrency & Competing Requests

### Scenario D
Two simultaneous requests arrive requesting 2 backpacks when only 2 exist in warehouse:
- Both requests initiate a `@Transactional` call.
- The first thread acquires a pessimistic lock (`PESSIMISTIC_WRITE` $\rightarrow$ `SELECT ... FOR UPDATE`) on the `BAG-001` row.
- The second thread is paused until the first transaction commits.
- When Thread 1 finishes, `reserved_stock` is updated to 2 (available: 0).
- Thread 2 obtains the lock, inspects `getAvailableStock()`, finds 0 available $< 2$ requested, and throws an `InsufficientStockException`.
- **Result**: Exactly one request succeeds. Zero overselling. Verified via automated test `testConcurrencyCompetingRequests()`.
