# Project Tracker

This file tracks the overarching phases of the Distributed Job Scheduler. Use this to maintain visibility on pending features, current roadblocks, and completed milestones based on the Execution Plan and Architecture.

## WEEK 1: Foundation (Containers + Skeleton)
- [x] Initialize Spring Boot projects (`submission-service`, `worker-service`, `shared-kernel`).
- [x] Configure Docker Compose (PostgreSQL, Redis, Kafka, Zookeeper).
- [x] Resolve network and port collisions (`5435`).
- [x] Unify Maven reactor — all modules inherit from root POM (`3.3.5`).
- [x] Fix `spring-boot-maven-plugin` to skip on root `[pom]` module.
- [x] Create `start-all.sh` — one-command parallel startup of all services.
- [x] Create `Makefile` for `make start / stop / build / test / status / smoke`.
- [x] Write `README.md` with full architecture, tech stack, and run instructions.

## WEEK 2-3: DOMAIN LAYER (NO INFRASTRUCTURE)
- [x] Implement Aggregate Root: `Job`, `JobStatus`, `Priority`.
- [x] Implement Domain Utilities: `BackoffCalculator` with full jitter.
- [x] Implement Idempotency constraints: `IdempotencyGuard` (with checkAndRegister).
- [x] Write Domain Unit Tests (`JobTest`, `BackoffCalculatorTest`).

## WEEK 4-5: Database + Repository
- [x] Initialize Flyway migrations (`V1`, `V2`, `V3`).
- [x] PostgreSQL Repository Implementation: `JobRepositoryImpl` with `JdbcClient`.
- [x] Fix `Instant → Timestamp` mapping bug in `JdbcClient.param()`.
- [x] Define Outbox and Audit Log SQL schemas.
- [x] `JobRepositoryIntegrationTest` with Testcontainers (`disabledWithoutDocker = true`).

## WEEK 6: REDIS QUEUE
- [x] Redis Atomic Transactions: `pop_job.lua`, `renew_lease.lua` (both services).
- [x] Redis Queue Implementation: `RedisQueueClient` (submission), `WorkerRedisClient` (worker).
- [x] Connect Worker Poller to Redis via `QueuePoller`.

## WEEK 7: RESILIENCE PATTERNS
- [x] Implement `RetryHandler` (exponential backoff + DLQ on max retries).
- [x] Integrate Resilience4j circuit breaker for webhooks (`WebhookClient`).
- [x] Implement `GracefulShutdown` (drain in-flight jobs, 30s max).

## WEEK 8: WORKER EXECUTION ENGINE
- [x] Implement `QueuePoller` — 100ms polling, Java-17-compatible cached thread pool.
- [x] Implement `JobExecutor` — fetch → execute → mark status, metrics recording.
- [x] Implement `HandlerRegistry` — plug-in routing for email, report, notification, export, test.
- [x] Implement `MetricsService` — Micrometer counters/timers/gauges.

## WEEK 9: API & CONTROLLERS
- [x] Implement `JobController` (POST /api/v1/jobs, GET /api/v1/jobs/{id}, ping).
- [x] `JobSubmitRequest` DTO with Bean Validation (@NotBlank, @Min, @Max).
- [x] `JobStatusResponse` and `JobSubmitResponse` DTOs.
- [x] `GlobalExceptionHandler` — RFC 7807 ProblemDetail for 400/404/409/500.
- [x] `JobSubmissionService` — idempotency → persist → enqueue.
- [x] `WorkerController` — GET /api/v1/worker/status, ping.
- [x] Swagger / OpenAPI via SpringDoc (`/swagger-ui.html`, `/api-docs`).

## WEEK 10: EVENTING + OUTBOX
- [x] `OutboxPoller` — polls every 1s, publishes to Kafka `job.events` topic.
- [x] `JobEventConsumer` — Kafka listener in worker-service, routes event types.
- [x] `OpenApiConfig` — full API description with server URLs.

## WEEK 11: OBSERVABILITY
- [x] Prometheus scrape config (`prometheus/prometheus.yml`) targeting both services.
- [x] `/actuator/prometheus` enabled on both services.
- [x] `MetricsService` — job.completions counter, job.execution.duration timer, queue.depth gauge.
- [x] Logback structured console logging (`logback-spring.xml`).
- [ ] Distributed tracing with Zipkin/Jaeger (not yet implemented).

## WEEK 12: TESTING & LOAD
- [x] `smoke-test.sh` — 16-check end-to-end smoke test (all 16 passing).
- [x] Build verified: `mvn clean install -DskipTests` ✅ (3.2s)
- [x] Unit tests: `mvn test` ✅ (all passing)
- [ ] JMeter / K6 load test scripts.
- [ ] Chaos test script.

## SECURITY (FUTURE)
- [ ] Configure Spring Security with JWT / Keycloak.
- [ ] Vault secrets integration (`VaultSecretRetriever`).
