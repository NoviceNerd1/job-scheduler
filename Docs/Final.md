# Final Technical Manifesto: Distributed Job Queue System

This document is the definitive, end-to-end technical breakdown of the Distributed Job Queue System. It covers every architectural decision, data structure, and resilience pattern from the ground up.

---

## 1. Executive Summary
- **What**: A fault-tolerant, priority-aware distributed task scheduler.
- **Why**: To provide "At-Least-Once" delivery guarantees and decouple synchronous API requests from long-running background tasks.
- **Performance Targets**: 10,000+ jobs/second throughput, <100ms submission latency, sub-millisecond dequeue latency.

---

## 2. Core Architecture & Strategic DDD
The system is divided into four primary **Bounded Contexts** to maintain a clean separation of concerns:

### Context Map
1. **Job Submission**: Entry point. Ownership of REST API, validation, and idempotency.
2. **Queue Management**: The "Holding Area." Ownership of priority logic using Redis Sorted Sets.
3. **Worker Execution**: The "Engine." Ownership of leasing, heartbeats, and pluggable job handlers.
4. **Retry & DLQ**: The "Safety Net." Ownership of exponential backoff and failed job analysis.

### Internal Service Layering
Each service follows a strict DDD layering pattern to ensure testability and maintainability:
1. **Controller Layer**: (`JobController`) Handles REST/OpenAPI, request validation, and RFC 7807 error mapping.
2. **Application Layer**: (`JobSubmissionService`, `JobExecutor`) Orchestrates use cases, manages transactions, and interacts with infrastructure ports.
3. **Domain Layer**: (`Job` Aggregate, `IdempotencyGuard`) Pure business logic, state machine transitions, and invariants. No infrastructure dependencies.
4. **Infrastructure Layer**: (`PostgresJobRepository`, `RedisQueueClient`) Implementation of ports for persistence, messaging (Redis Lua), and Kafka eventing.

---

## 3. Deep Dive: Data Persistence & Optimization

### A. PostgreSQL: The System of Record
We use Postgres not just for storage, but as a consistent state machine.
- **Partitioning**: The `jobs` and `audit_log` tables are **partitioned by range (created_at month)**. This allows for instant cleanup of old data by dropping partitions rather than expensive `DELETE` queries.
- **Optimistic Locking**: Every job record has a `version` column. This prevents "Lost Updates" if two processes try to update the same job simultaneously.
- **Index Strategy**:
    - `idx_jobs_pending_retry`: A **Partial Index** (`WHERE status IN ('PENDING', 'RETRY')`) that keeps the index small and lookups extremely fast for the poller.
    - `idx_jobs_idempotency`: Ensures rapid deduplication lookups by `idempotency_key` and `created_by`.

### B. Transactional Outbox Pattern
To solve the **Dual-Write Problem** (writing to DB and Kafka atomically):
1. Inside a single Postgres transaction, we save the **Job Status** and the **Event** to an `outbox` table.
2. A background `OutboxPoller` reads unpublished events and pushes them to Kafka.
3. This ensures that an event is **never** sent if the DB transaction fails, and is **eventually** sent if Kafka is temporarily down.

---

## 4. Deep Dive: Redis & Atomic Operations

### The Priority Queue (Sorted Sets)
We use Redis **Sorted Sets** where:
- **Member**: Job ID (String)
- **Score**: `(PriorityLevel * 10^13) + Timestamp`
This ensures O(log N) access to the highest priority job while maintaining FIFO order for same-priority tasks.

### Atomic Claiming (Lua Scripting)
Standard `LPOP` isn't enough. We need to "Claim and Lease" in one step. Our `pop_job_with_lease.lua` script:
1. Calls `ZPOPMIN` to get the highest priority Job ID.
2. Calls `HSET` to record the **Worker ID** and **Lease Expiry** in a separate hash.
3. Updates a Redis **Gauge** for real-time queue depth monitoring.
**Why?** Redis executes Lua scripts as a single atomic unit. No two workers can ever "race" to claim the same job.

---

## 5. Resilience & Fault Tolerance

### A. Timeout Hierarchy
| Layer | Timeout | Action on Failure |
|-------|---------|-------------------|
| **API submission** | 5s | 504 Gateway Timeout |
| **Postgres Query** | 1s | SQL Statement Cancel |
| **Job Execution** | 30s | Kill thread, record Failure |
| **Lease TTL** | Timeout + 5s | Job becomes available for other workers |
| **Webhook Call** | 5s | Circuit Breaker trips |

### B. Resilience4j Configuration
- **Circuit Breaker**: Uses a **sliding window of 100 calls**. If 50% fail, it opens for 10s, protecting downstream services.
- **Retries**: 3 attempts with a 1000ms delay and a 2x multiplier.
- **Rate Limiting**: Integrated `Bucket4j` in the submission service to prevent noisy neighbors from overwhelming the system (1000 requests/min default).

---

## 6. Observability & Monitoring

### Prometheus Alerting Rules
- **HighQueueDepth**: Alerts if CRITICAL jobs > 10,000 for 5 minutes.
- **WorkerHeartbeatMissing**: Alerts if a worker hasn't reported in 30 seconds.
- **HighFailureRate**: Alerts if >5% of jobs are moving to `DEAD` state.

### Standardized Error Handling (RFC 7807)
The API returns `ProblemDetails` JSON for all errors:
- `type`: URI to documentation of the error.
- `title`: Short human-readable summary.
- `status`: HTTP status code.
- `detail`: Detailed explanation of why this specific request failed.

---

## 7. Architecture Decision Records (ADRs)

### ADR-001: Redis vs Kafka for Primary Queue
- **Decision**: Redis for Active Queue, Kafka for Event Stream.
- **Rationale**: Redis provides sub-millisecond priority dequeue. Kafka provides persistent logs for downstream consumers.

### ADR-002: At-Least-Once Delivery
- **Decision**: Accept at-least-once delivery; enforce **Idempotency** in handlers.
- **Rationale**: Exactly-once is too expensive (2PC/XA). Idempotency is scalable and resilient to network retries.

### ADR-003: Virtual Threads (Java 21)
- **Decision**: Use Virtual Threads for high-concurrency worker execution.
- **Rationale**: Replaces heavy OS threads with lightweight "Green Threads," allowing us to handle 10,000+ concurrent jobs on a single worker node.

### ADR-004: Monthly Partitioning
- **Decision**: Range partition the Job table by month.
- **Rationale**: Millions of jobs create massive tables. Partitioning keeps indexes small and allows "archiving by dropping" which is O(1) in performance.

---

## 8. Deployment & Operations
- **JVM Flags**: Optimized for containers with `-XX:MaxRAMPercentage=75.0` and `-XX:+ExitOnOutOfMemoryError`.
- **Graceful Shutdown**: On `SIGTERM`, the worker stops polling, waits for active jobs to finish (up to their timeout), and then releases its connections. No job is left "half-done" without a record.
