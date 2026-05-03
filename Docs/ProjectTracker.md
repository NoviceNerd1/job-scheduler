# Project Tracker

This file tracks the overarching phases of the Distributed Job Scheduler. Use this to maintain visibility on pending features, current roadblocks, and completed milestones based on the Execution Plan and Architecture.

## WEEK 1: Foundation (Containers + Skeleton)
- [x] Initialize Spring Boot projects (`submission-service`, `worker-service`, `shared-kernel`).
- [x] Configure Docker Compose (PostgreSQL, Redis, Kafka, Zookeeper).
- [x] Resolve network and port collisions (`5435`).
- [x] Unify Maven reactor — all modules inherit from root POM (`3.3.5`).
- [x] Fix `spring-boot-maven-plugin` to skip on root `[pom]` module.
- [x] Create `start-all.sh` — one-command parallel startup of all services.
- [x] Create `Makefile` for `make start / stop / build / test / status`.
- [x] Write `README.md` with full architecture, tech stack, and run instructions.

## WEEK 2-3: DOMAIN LAYER (NO INFRASTRUCTURE)
- [x] Implement Aggregate Root: `Job`, `JobStatus`, `Priority`.
- [x] Implement Domain Utilities: `BackoffCalculator` with full jitter.
- [x] Implement Idempotency constraints: `IdempotencyGuard`.
- [x] Write Domain Unit Tests (`JobTest`, `BackoffCalculatorTest`).

## WEEK 4-5: Database + Repository
- [x] Initialize Flyway migrations (`V1`, `V2`, `V3`).
- [x] PostgreSQL Repository Implementation: `JobRepositoryImpl` with `JdbcClient`.
- [x] Fix `Instant → Timestamp` mapping bug in `JdbcClient.param()`.
- [x] Define Outbox and Audit Log SQL schemas.
- [x] `JobRepositoryIntegrationTest` with Testcontainers (`disabledWithoutDocker = true`).

## WEEK 6: REDIS QUEUE
- [x] Redis Atomic Transactions: `pop_job.lua`, `renew_lease.lua`.
- [x] Redis Queue Implementation: `RedisQueueClient`.
- [ ] Connect Worker Poller to Redis.

## WEEK 7: RESILIENCE PATTERNS
- [ ] Implement `RetryHandler`.
- [ ] Integrate Resilience4j (Circuit Breakers for webhooks).
- [ ] Implement Graceful Shutdown config.

## WEEK 8: WORKER EXECUTION ENGINE
- [ ] Implement `QueuePoller` (Evaluate Virtual Thread usage / Thread Pool constraints).
- [ ] Implement `JobExecutor` and Context management.
- [ ] Implement `HandlerRegistry` for dynamic job routing.

## WEEK 9: API & CONTROLLERS
- [ ] Implement `SubmissionController` and REST definitions.
- [ ] Global Exception Handlers.
- [ ] Swagger / OpenAPI configuration.

## WEEK 10: SECURITY & SECRETS
- [ ] Configure Spring Security with JWT.
- [ ] Setup Vault interactions (`VaultSecretRetriever`).

## WEEK 11: OBSERVABILITY
- [ ] Implement `MetricsService` and Prometheus integration.
- [ ] Distributed Tracing with Zipkin/Jaeger.

## WEEK 12: LOAD TESTING & TUNING
- [ ] JMeter / K6 scripts.
- [ ] Kafka Outbox Poller Setup (`OutboxEvent` creation).
