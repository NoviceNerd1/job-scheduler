# Distributed Job Scheduler - Project Completion Log

This document tracks all the exact implementations, changes, and updates made to the codebase to establish the foundational architecture, ensure compatibility, and make the project fully runnable based on the `Plan.md`, `ExecutionPlan.md`, and `Project_architecture.md`.

## WEEK 1: Infrastructure Setup & Corrections
### What
Resolved database connection failures preventing the microservices from starting and correctly aligned the Maven setup.
### How & Why
- **`docker-compose.yml`**: Updated the exposed PostgreSQL port mapping from `5432:5432` to `5435:5432`.
  - *Why*: Port 5432 was already occupied on the host system. This caused the Docker container to fail silently while the local machine's native PostgreSQL instance intercepted traffic, leading to a `password authentication failed` error.
- **`application.yaml`**: Updated `datasource.url` to use port `5435` and set `spring.jpa.hibernate.ddl-auto` to `validate` in both `submission-service` and `worker-service`.
  - *Why*: To safely enforce Flyway migrations and align with the corrected Docker configuration.
- **`pom.xml` (Root)**: Changed `<java.version>21</java.version>` to `<java.version>17</java.version>`.
  - *Why*: The system JDK installed is Java 17. A mismatch caused the Spring Boot compilation to fail completely.
- **`pom.xml` (Services)**: Corrected `shared-kernel` version from `${project.version}` to `1.0.0-SNAPSHOT`.
  - *Why*: The child modules had version `0.0.1-SNAPSHOT` while `shared-kernel` inherited `1.0.0-SNAPSHOT` from the parent. This mismatch broke Maven reactor dependencies.

## WEEK 2-3: `shared-kernel` Domain Module Implementation
### What
Bootstrapped the common domain models shared between microservices exactly as defined in `ExecutionPlan.md`.
### How & Why
- **`JobStatus.java`**: Defined the enum tracking job states (`PENDING`, `LEASED`, `RUNNING`, etc.) and the state machine transition logic.
- **`Priority.java`**: Defined the priority weighting mechanism (1-5).
- **`Job.java`**: Implemented the Aggregate Root containing the core data, failure tracking (`recordFailure`), and transition logic (`transitionTo`).
- **`BackoffCalculator.java`**: Implemented exponential backoff algorithms with full jitter for task retries.
- **`IdempotencyGuard.java` (submission-service)**: Refactored `Thread.startVirtualThread()` to use `new Thread(() -> ...).start()`.
  - *Why*: Virtual threads are a Java 21 feature. Traditional threading was used to restore Java 17 compatibility.
- **`shared-kernel/pom.xml`**: Added `junit-jupiter` and `assertj-core` dependencies to resolve missing test packages.

## WEEK 4-6: Storage & Queuing Core Implementation
### What
Implemented the persistent storage and message queuing components within the `submission-service` according to architecture plans.
### How & Why
- **`JobRepository.java` & `JobRepositoryImpl.java`**: Implemented `JdbcClient` interactions with PostgreSQL for CRUD operations, mapping records back to the `Job` aggregate. Manually added necessary class imports (`JobStatus`, `Priority`) missing from the original pseudocode.
- **`RedisQueueClient.java`**: Implemented Spring Data Redis commands.
- **Lua Scripts**: Wrote `pop_job.lua` and `renew_lease.lua` in `src/main/resources/lua/` to handle sorted-set queue popping securely.
- **Pruned Advanced Stubs**: Removed incomplete pseudo-code fragments generated from the markdown (`SecurityConfig.java`, `VaultSecretRetriever.java`, `OutboxPoller.java`, `QueuePoller.java`).
  - *Why*: These snippets referenced undefined classes (e.g., `OutboxEvent`). To ensure the core skeleton aligns with the "fully working and runnable" goal for Weeks 1-6, they were pruned. These will be progressively re-added in Weeks 7-12.

## WEEK 4-6: Testing & Validation
### What
Ran the integration and unit tests across modules, discovered an ORM/JDBC mapping issue, and successfully resolved it to ensure the entire suite is `BUILD SUCCESS`.
### How & Why
- **`JobRepositoryImpl.java`**: Converted `java.time.Instant` to `java.sql.Timestamp` inside the `.param()` binds for the `updated_at` and `created_at` fields.
  - *Why*: `JdbcClient` and the PostgreSQL JDBC driver could not infer the SQL type directly for `Instant` objects on `INSERT` queries, leading to a `BadSqlGrammarException`. Explicitly casting to `Timestamp` resolved the issue.
- **`JobRepositoryIntegrationTest.java`**: Added `@Import(JobRepositoryImpl.class)` to correctly wire the repository into the `@JdbcTest` context, allowing the Testcontainers `postgres:16` database to correctly assert the save-and-retrieve workflow.

## WEEK 1 (Revisited): Maven Multi-Module POM Restructure
### What
Diagnosed and corrected a critical structural flaw in the Maven reactor setup that caused `mvn spring-boot:run` to fail from the root directory.
### Root Cause
- The root `pom.xml` had Spring Boot version `3.2.5`; child modules had `3.5.14` — two totally different versions that never communicated.
- Child modules each declared `spring-boot-starter-parent` as their direct parent instead of inheriting from the `jobqueue-system` root POM. This **broke the multi-module reactor pattern entirely**.
- The `spring-boot-maven-plugin` in the root POM was trying to execute `spring-boot:run` on a `[pom]` packaging module with no main class.
### Fixes Applied
- **Root `pom.xml`**: Unified Spring Boot to `3.3.5`. Added `spring-cloud-dependencies` and `testcontainers-bom` BOM imports centrally. Set `spring-boot-maven-plugin` to `<skip>true</skip>` at root level so it never fires on the parent POM.
- **`submission-service/pom.xml`** and **`worker-service/pom.xml`**: Changed `<parent>` from `spring-boot-starter-parent` to `com.jobqueue:jobqueue-system:1.0.0-SNAPSHOT`. This correctly hooks both services into the reactor. Explicit `<skip>false</skip>` in both service POMs re-enables the boot plugin only where needed.
- **Both `application.yaml`**: Removed explicit Hibernate dialect config (triggers a deprecation warning on Hibernate 6). Set `spring.jpa.open-in-view: false` to prevent the open-session-in-view anti-pattern warning.
### How To Run
```bash
# Run full build from root (always safe)
mvn clean install -DskipTests

# Run each service individually
cd submission-service && mvn spring-boot:run

cd worker-service && mvn spring-boot:run
```

## Startup Hardening

### What
Resolved runtime failures caused by stale background processes and LiveReload port conflicts.

### Root Cause
- Running `./start-all.sh` or `mvn spring-boot:run` while a previous session was still alive caused `Port 8081/8082 already in use` — a hard startup failure with no automatic recovery.
- Spring DevTools LiveReload binds to port `35729` by default. With two services running, the second produced `WARN: Unable to start LiveReload server`, occasionally blocking context initialization.

### Fixes Applied
- **`start-all.sh`**: Added `kill_port()` using `lsof -ti:<port> | xargs kill -9` to forcibly clear ports `8081`, `8082`, and `35729` before every launch. Added `--status` flag for live health polling.
- **Both `application.yaml`**: Added `spring.devtools.livereload.enabled: false` to eliminate port 35729 contention. Added `logging.level.com.jobqueue: DEBUG`.
- **Verified**: `./start-all.sh` auto-clears stale processes, rebuilds, and confirms both services `UP` via `/actuator/health`.

---

## WEEK 7: Resilience Patterns

### What
Implemented `RetryHandler`, `WebhookClient` with Resilience4j, and `GracefulShutdown`.

### How & Why
- **`RetryHandler.java`** (`worker-service/application`): On job failure, calculates next retry time using `BackoffCalculator.calculateNextRetry()` and updates DB with `status=RETRY`, `next_retry_at`, `attempts`. Once `attempts >= maxRetries`, marks status `DEAD`.
  - *Why*: Prevents runaway retry loops. Jittered backoff avoids thundering-herd when many jobs fail simultaneously.
- **`WebhookClient.java`** (`worker-service/infrastructure/webhook`): `@CircuitBreaker(name="webhook")` from Resilience4j protects downstream webhook URLs. Circuit opens after 50% failure rate over 10 calls; fallback logs and queues for retry.
  - *Why*: Downstream callback URLs are unreliable. Circuit breaker prevents cascading failures into the job executor thread pool.
- **`GracefulShutdown.java`** (`worker-service/config`): `ApplicationListener<ContextClosedEvent>` — signals `QueuePoller.setRunning(false)` then polls `JobExecutor.getActiveCount()` every 1s, up to 30s.
  - *Why*: Without graceful shutdown, in-flight jobs are interrupted mid-execution, leaving DB in `RUNNING` state permanently.
- **`resilience4j.circuitbreaker.instances.webhook`** in `application.yaml`: `slidingWindowSize=10`, `failureRateThreshold=50`, `waitDurationInOpenState=30s`.

---

## WEEK 8: Worker Execution Engine

### What
Implemented the full worker execution pipeline: `HandlerRegistry` → `JobExecutor` → `QueuePoller` + `MetricsService`.

### How & Why
- **`HandlerRegistry.java`**: `ConcurrentHashMap<String, Consumer<Job>>` maps job type strings to handler functions. Built-in handlers: `email.send`, `report.generate`, `notification.push`, `data.export`, `test`. New handlers added via `register(type, handler)` — no executor changes needed.
  - *Why*: Open/Closed principle — add new job types without touching the execution engine.
- **`JobExecutor.java`**: Fetches job from DB, validates status is not already terminal, marks `RUNNING`, delegates to `HandlerRegistry`, marks `COMPLETED`. Tracks `AtomicInteger activeCount` for graceful shutdown coordination.
  - *Why*: Centralises all execution state transitions. The poller never touches the DB directly.
- **`QueuePoller.java`**: `@Scheduled(fixedDelay=100)` polls `WorkerRedisClient.pop()`, submits to `CachedThreadPool` (Java 17 compatible — replaced `Thread.ofVirtual()` which requires Java 21).
  - *Why*: 100ms poll rate gives sub-200ms job pickup latency without busy-waiting. Thread pool bounded by JVM heap, not OS threads.
- **`MetricsService.java`**: Micrometer `counter("job.completions")`, `timer("job.execution.duration")`, `gauge("queue.depth")`, `gauge("worker.active_jobs")`.
- **`WorkerConfig.java`**: `@EnableScheduling` + `RedisTemplate<String,String>` bean.

**Fix**: Replaced `Executors.newThreadPerTaskExecutor(Thread.ofVirtual()...)` with `Executors.newCachedThreadPool()` — Java 17 does not support virtual threads without `--enable-preview`.

---

## WEEK 9: REST API & Controllers

### What
Full REST API for job submission and status in `submission-service`, worker diagnostics in `worker-service`.

### How & Why
- **`JobSubmitRequest`** (record): `@NotBlank type`, `@NotNull payload`, `@Min/@Max priority`, `@Min/@Max maxRetries`, `idempotencyKey`, `timeoutMs`. Default values applied via `effectivePriority()` / `effectiveMaxRetries()` / `effectiveTimeout()` methods.
- **`JobController`**: `POST /api/v1/jobs` → 202 Accepted. `GET /api/v1/jobs/{id}` → 200. `GET /api/v1/jobs/ping` → pong.
- **`JobSubmissionService`**: Generates idempotency key if absent → `IdempotencyGuard.checkAndRegister()` → `Job.create()` → `JobRepository.save()` → `RedisQueueClient.enqueue()`.
- **`GlobalExceptionHandler`**: RFC 7807 `ProblemDetail` for `JobNotFoundException → 404`, `DuplicateJobException → 409`, `MethodArgumentNotValidException → 400`, `Exception → 500`.
- **`WorkerController`**: `GET /api/v1/worker/status` — queue depths per priority + active job count. `GET /api/v1/worker/ping`.
- **`OpenApiConfig`**: Configures SpringDoc `OpenAPI` bean with API title, description, contact, license, server URLs.
- **Swagger UI**: Available at `/swagger-ui.html` on both ports (302 redirect to `/swagger-ui/index.html`).

---

## WEEK 10: Eventing + Outbox Pattern

### How & Why
- **`OutboxEvent`** (record): Maps `outbox` table columns.
- **`OutboxPoller`**: `@Scheduled(fixedDelay=1000)` — queries `outbox WHERE published=FALSE LIMIT 100`, publishes each to Kafka topic `job.events`, marks `published=TRUE`. `writeEvent()` method for writing events within a job transaction.
  - *Why*: Transactional outbox guarantees at-least-once delivery without distributed transactions. The job save and the event write happen in the same JDBC connection.
- **`JobEventConsumer`**: `@KafkaListener(topics="job.events", groupId="worker-group")` — routes to `handleJobCompleted`, `handleJobFailed`, `handleJobSubmitted`.
- **Kafka config**: Producer `StringSerializer`, consumer `earliest` offset, `worker-group`.

---

## WEEK 11: Observability

### How & Why
- **`prometheus/prometheus.yml`**: Scrapes `/actuator/prometheus` on both services via `host.docker.internal`.
- **`logback-spring.xml`**: Pattern layout console appender with `DEBUG` for `com.jobqueue` package, `INFO` root.
- **`application.yaml` both services**: `management.endpoints.web.exposure.include: health,info,prometheus,metrics`, `show-details: always`.

---

## WEEK 12: Testing & Verification

### What
End-to-end smoke test script covering all critical API paths.

### How & Why
- **`scripts/smoke-test.sh`**: 10 test groups, 16 individual checks:
  1. Health endpoints (submission + worker)
  2. Swagger UI (both, following 302 redirect)
  3. API docs JSON
  4. Home pages
  5. Prometheus metrics
  6. Ping endpoints
  7. Job submission (POST → 202)
  8. Job status lookup (GET → 200)
  9. Idempotency (duplicate key → 409)
  10. Validation (missing type → 400)
  11. Worker status (queue depths + active count)
- **Result**: 16/16 PASSED ✅
- **Build**: `mvn clean install -DskipTests` ✅ (3.2s)
- **Tests**: `mvn test` ✅ (all passing)
- **`Makefile`**: Added `smoke`, `infra-up`, `infra-down`, `help` targets.

---

## Final Deep Code Analysis & Corrections

### What
Conducted a deep review of all source files to catch logical bugs and missing integrations that tests did not cover.

### How & Why
- **Outbox Pattern Missing Transaction**: `JobSubmissionService.submit()` was not calling `OutboxPoller.writeEvent()` and was missing a `@Transactional` annotation.
  - *Fix*: Injected `OutboxPoller`, added `@Transactional` to `submit()`, and called `writeEvent` inside the same method, ensuring atomic commits of both the job and the outbox event to PostgreSQL.
- **Outbox Schema Mismatch**: `OutboxPoller` generated SQL referencing `aggregate_type`, but the Flyway schema `V2__create_outbox_table.sql` defined `event_id`.
  - *Fix*: Updated `OutboxPoller` SQL (both `SELECT` and `INSERT`) and the `OutboxEvent` record to strictly match the deployed V2 schema. Added `java.util.UUID.randomUUID()` generation for the `event_id`.
- **Metrics High Cardinality Bug**: `JobExecutor` catch block recorded `metricsService.recordJobCompletion(jobId, "failure", duration)`. Passing a UUID as a metric tag value creates infinite cardinality, crashing Prometheus.
  - *Fix*: Added a safe `findJobById(jobId)` lookup in the catch block to resolve the `jobType` string, and passed `jobType` as the tag value instead.


