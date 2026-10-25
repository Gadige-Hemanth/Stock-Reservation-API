# Initial Implementation Plan: Stock Reservation API

## 1. Problem Restatement & Acceptance Criteria

### Problem
Build a reliable, thread-safe Java REST backend for warehouse inventory management where clients can reserve multiple items, confirm reservations (completing the purchase), or cancel them (releasing reserved stock back to available stock).

### Acceptance Criteria
1. **Inventory Consistency**:
   - `available_stock` = `total_stock` - `reserved_stock`.
   - All three values must remain integers $\ge 0$ at all times.
2. **All-or-Nothing Multi-Item Reservation**:
   - A reservation request with multiple items must either completely reserve all items or fail atomically with zero changes to inventory.
3. **Lifecycle State Transitions**:
   - Allowed transitions: `ACTIVE -> CONFIRMED` and `ACTIVE -> CANCELLED`.
   - Forbidden transitions: `CONFIRMED -> CANCELLED` and `CANCELLED -> CONFIRMED` (rejected with HTTP 409 Conflict).
   - Idempotency: Repeating confirmation of a `CONFIRMED` reservation or cancellation of a `CANCELLED` reservation returns the current state safely without mutating inventory a second time.
4. **Concurrency & Race Condition Prevention**:
   - When concurrent requests compete for limited stock (e.g., two concurrent requests for 2 backpacks when only 2 exist), exactly one must succeed and the other must fail without overselling.
5. **Persistence Across Restarts**:
   - Stock counts and reservations must persist on disk across server restarts.
   - Seed data must only initialize empty tables and never overwrite live inventory on restart.

---

## 2. Assumptions & Ambiguities

- **Duplicate Items in a Request**: If a client sends `[{"productId": 1, "qty": 2}, {"productId": 1, "qty": 3}]`, the service aggregates them to `{"productId": 1, "qty": 5}` before checking stock to avoid multiple lock acquisitions on the same row.
- **Reservation Expiry**: As noted in the assignment brief, automated timeout/expiry is intentionally excluded.
- **Customer Identity**: Authentication and user accounts are omitted as requested to keep the design lean and focused on core stock concurrency.
- **Database Choice**: File-backed relational storage (H2 file mode `jdbc:h2:file:./data/inventorydb`) with full transactional ACID support is used by default so the application runs immediately without requiring external database server installations, with an optional profile for PostgreSQL.

---

## 3. API Contract & Endpoints

| Method | Endpoint | Description | Status Codes |
|---|---|---|---|
| `GET` | `/api/products` | View all products and inventory levels | 200 OK |
| `POST` | `/api/reservations` | Create a multi-item reservation | 201 Created, 400 Bad Request, 409 Conflict |
| `GET` | `/api/reservations/{id}` | Retrieve reservation details & items | 200 OK, 404 Not Found |
| `POST` | `/api/reservations/{id}/confirm` | Confirm reservation (deducts total & reserved) | 200 OK, 404 Not Found, 409 Conflict |
| `POST` | `/api/reservations/{id}/cancel` | Cancel reservation (releases reserved) | 200 OK, 404 Not Found, 409 Conflict |

---

## 4. Database Schema Design

### `products` table
- `id` (BIGINT, PRIMARY KEY, AUTO_INCREMENT)
- `sku` (VARCHAR(50), UNIQUE, NOT NULL)
- `name` (VARCHAR(255), NOT NULL)
- `total_stock` (INT, NOT NULL, CHECK (total_stock >= 0))
- `reserved_stock` (INT, NOT NULL, CHECK (reserved_stock >= 0))
- Constraint: `CHECK (total_stock >= reserved_stock)`

### `reservations` table
- `id` (BIGINT, PRIMARY KEY, AUTO_INCREMENT)
- `status` (VARCHAR(20), NOT NULL) -- ACTIVE, CONFIRMED, CANCELLED
- `created_at` (TIMESTAMP, NOT NULL)
- `updated_at` (TIMESTAMP, NOT NULL)

### `reservation_items` table
- `id` (BIGINT, PRIMARY KEY, AUTO_INCREMENT)
- `reservation_id` (BIGINT, FOREIGN KEY -> reservations.id, NOT NULL)
- `product_id` (BIGINT, FOREIGN KEY -> products.id, NOT NULL)
- `quantity` (INT, NOT NULL, CHECK (quantity > 0))

---

## 5. Concurrency & Transaction Management

1. **Transaction Boundaries**: All reservation creation, confirmation, and cancellation operations run under `@Transactional(isolation = Isolation.READ_COMMITTED)`.
2. **Pessimistic Locking (`SELECT ... FOR UPDATE`)**:
   - To prevent overselling under concurrent requests, product rows are locked using `LockModeType.PESSIMISTIC_WRITE`.
   - **Deadlock Avoidance**: When a reservation contains multiple products, product IDs are sorted in ascending order before locking rows. This enforces a consistent lock acquisition order across all threads.

---

## 6. Implementation Sequence & Test Strategy

1. **Step 1: Domain Entities & Repositories** (Product, Reservation, ReservationItem with pessimistic lock query).
2. **Step 2: Business Logic Service** (Atomicity, stock checks, state transition guards, idempotent repeated calls).
3. **Step 3: REST API Controllers & Error Handling** (GlobalExceptionHandler with standardized RFC-7807/ProblemDetail responses).
4. **Step 4: Seed Data & Persistence Config** (Conditional seed loading on empty DB).
5. **Step 5: Automated Integration Tests**:
   - Full lifecycle test (Create -> Confirm).
   - Cancellation test (Create -> Cancel).
   - Atomic failure test (Insufficient stock for 1 item aborts entire batch).
   - Concurrent competing requests test (Simultaneous requests for same stock; verify no overselling).
   - Idempotency test (Repeated confirm/cancel).
