# AI Usage & Retrospective (`AI_USAGE.md`)

## 1. AI Tools Used
- **Google Antigravity / Gemini 3.8 Flash**: Assisted with initial project scaffolding, concurrency scenario analysis, dead-lock prevention patterns, and automated test setup.

## 2. Tasks Assisted by AI
- Drafting the initial architecture design and verifying edge cases from the assignment brief.
- Formulating the pessimistic locking strategy (`LockModeType.PESSIMISTIC_WRITE`) for JPA.
- Writing multi-threaded concurrency integration tests using `ExecutorService` and `CountDownLatch`.
- Generating comprehensive `curl` examples and API documentation.

## 3. Meaningful Suggestions & Decisions

### Accepted Suggestion: Sort Product IDs Before Locking
- **Suggestion**: When locking multiple product rows in a single reservation transaction, sort the product IDs in ascending numerical order (`1, 2, 3...`) prior to executing `findByIdWithLock`.
- **Reason**: Different transactions reserving overlapping items in different orders (e.g., Tx1: Pen then Notebook; Tx2: Notebook then Pen) can cause circular database deadlocks. Sorting IDs guarantees a globally uniform lock acquisition order across all threads.

### Modified / Rejected Suggestion: Soft-locking / Optimistic Retry Loop vs Pessimistic Locking
- **Initial Suggestion**: Use `@Version` optimistic locking and retry on conflict.
- **Decision to Reject & Modify**: For high-contention warehouse stock with limited inventory (e.g., only 2 backpacks), optimistic locking causes excessive retry exceptions and degraded throughput under spike loads. Pessimistic row locking (`SELECT ... FOR UPDATE`) is deterministic, queues requests cleanly at the database row level, and guarantees immediate stock checks without phantom reads.

## 4. Verification Strategy
- **Unit & Integration Testing**: Tested atomic rollbacks, lifecycle transitions, and data integrity using `@SpringBootTest`.
- **Concurrency Test**: Verified that two concurrent threads requesting 2 units from a stock of 2 resulting in exactly 1 success and 1 failure (`InsufficientStockException`), leaving 0 available units.
- **Persistence Verification**: Ran tests with a file-backed database (`jdbc:h2:file:./data/inventorydb`), restarted the application process, and verified that previously written stock records survived unchanged without re-seeding.

## 5. Retrospective & Trade-offs
- **Time Spent**: ~6.5 hours total (Planning: 40 mins, Implementation: 3.5 hours, Testing & Concurrency verification: 1.5 hours, Documentation: ~45 mins).
- **Known Limitations**:
  - In a distributed multi-node microservices architecture, row-level database locks can create contention bottlenecks across database instances. A distributed lock (e.g., Redis Redlock) or event-sourcing with an inventory ledger would be considered at larger scale.
  - No background worker exists to expire abandoned reservations (intentionally excluded per requirements).
- **Future Improvements**:
  - Add soft reservation expiration via scheduled job / event-driven delayed queues.
  - Implement idempotent request tokens (`Idempotency-Key` header) for network drop recovery.
