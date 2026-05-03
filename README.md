# Distributed Job Queue System

> **Enterprise-grade distributed job scheduling system** — Production patterns running entirely on your local machine via Docker Compose. Every pattern mirrors what runs at companies processing millions of jobs per second.

---

## What We Are Building

A **complete, production-ready distributed job queue system** with:

- **Reliable job submission** — REST API accepts jobs with idempotency keys to prevent duplicates
- **Priority-based queuing** — Jobs enqueued into Redis sorted sets (Priority 1–5), highest priority executed first
- **Distributed lease system** — Workers atomically claim jobs via Lua scripts, preventing double-execution across replicas
- **Exponential backoff retries** — Failed jobs retry with full-jitter backoff, dead-lettered after max attempts
- **Outbox pattern** — Guaranteed-delivery event publishing via PostgreSQL → Kafka transactional outbox
- **Observability** — Prometheus metrics, distributed tracing (Zipkin), structured logs

---

## Why We Are Building It

This project exists to demonstrate **principal-engineer-level distributed systems knowledge**:

| Pattern | What You Learn |
|---|---|
| Priority Redis sorted sets + Lua | Atomic multi-queue operations, race condition prevention |
| Distributed lease with TTL | At-least-once delivery semantics without dual-write |
| Outbox pattern | Guaranteed event publishing despite partial failures |
| Exponential backoff + full jitter | Thundering herd prevention |
| Multi-module Maven reactor | Enterprise Java project structure |
| Testcontainers integration tests | Hermetic, production-equivalent DB testing |

---

## System Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    CLIENTS (REST)                       │
│              POST /api/v1/jobs  (port 8081)             │
└────────────────────┬────────────────────────────────────┘
                     │
         ┌───────────▼───────────┐
         │   submission-service  │  :8081
         │  ┌─────────────────┐  │
         │  │  IdempotencyGuard│  │
         │  │  JobRepository  │  │
         │  │  RedisQueueClient│  │
         │  └────────┬────────┘  │
         └───────────┼───────────┘
                     │ enqueue()
          ┌──────────▼──────────┐
          │   Redis Sorted Sets │  :6379
          │  queue:priority:1-5 │
          └──────────┬──────────┘
                     │ pop_job.lua (atomic)
         ┌───────────▼───────────┐
         │    worker-service     │  :8082
         │  ┌─────────────────┐  │
         │  │   QueuePoller   │  │
         │  │   JobExecutor   │  │
         │  │   RetryHandler  │  │
         │  └─────────────────┘  │
         └───────────────────────┘
                     │
          ┌──────────▼──────────┐
          │     PostgreSQL      │  :5435
          │  jobs | outbox      │
          │  audit_log          │
          └─────────────────────┘
```

---

## Module Structure

```
jobqueue-system/
├── shared-kernel/          # Domain models shared across services
│   ├── model/Job.java      # Aggregate root
│   ├── model/JobStatus.java
│   ├── model/Priority.java
│   └── util/BackoffCalculator.java
│
├── submission-service/     # REST API + Job Persistence + Queue Publishing (port 8081)
│   ├── domain/IdempotencyGuard.java
│   ├── infrastructure/persistence/JobRepositoryImpl.java
│   └── infrastructure/queue/RedisQueueClient.java
│
└── worker-service/         # Job Execution + Polling + Retry (port 8082)
    └── WorkerServiceApplication.java
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.3.5 |
| Database | PostgreSQL 16 |
| Queue | Redis 7 (sorted sets + Lua) |
| Events | Apache Kafka |
| Migrations | Flyway |
| Build | Maven 3 (multi-module) |
| Testing | JUnit 5 + Testcontainers |

---

## Prerequisites

| Tool | Version | Check |
|---|---|---|
| Java | 17+ | `java -version` |
| Maven | 3.8+ | `mvn -version` |
| Docker Desktop | Latest | `docker --version` |
| Make | any | `make --version` |

---

## How to Run (Full Stack)

### Step 1 — Start Infrastructure (Docker)

```bash
cd jobqueue-system
docker compose up -d
```

This starts:
- **PostgreSQL** → `localhost:5435` (database: `jobqueue`, user: `jobqueue`)
- **Redis** → `localhost:6379`
- **Kafka** → `localhost:9092`
- **Zookeeper** → `localhost:2181`

Verify:
```bash
docker compose ps
```

### Step 2 — Build All Modules

```bash
# From jobqueue-system directory
mvn clean install -DskipTests
```

### Step 3 — Run All Services (Recommended)

```bash
# From jobqueue-system directory
chmod +x start-all.sh
./start-all.sh
```

This builds, then starts **both microservices in parallel background processes** with logs saved to `logs/`.

### Stop Everything

```bash
./start-all.sh --stop
```

---

## Alternative: Run Services Individually

Open **two separate terminals**:

**Terminal 1 — Submission Service (port 8081)**
```bash
cd jobqueue-system/submission-service
mvn spring-boot:run
```

**Terminal 2 — Worker Service (port 8082)**
```bash
cd jobqueue-system/worker-service
mvn spring-boot:run
```

> ⚠️ **Do NOT run `mvn spring-boot:run` from `jobqueue-system` root.** The root is a `[pom]` aggregator — it has no main class. Each microservice must be run from its own directory, or via `start-all.sh`.

---

## Using `make` (Quickstart)

```bash
cd jobqueue-system

make infra-up          # Start Docker infrastructure
make build             # Compile all modules
make start             # Start both microservices
make status            # Check health of both services
make smoke             # Run end-to-end smoke tests
make stop              # Stop all running services
make test              # Run all tests
make clean             # Clean build + logs
make infra-down        # Stop Docker infrastructure
```

---

## How to Use the Scheduler

Once the infrastructure and services are running, you can interact with the Job Scheduler using its REST API or via the Swagger UI.

### Swagger UI (Interactive API Docs)
- **Submission Service**: [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html)
- **Worker Service**: [http://localhost:8082/swagger-ui.html](http://localhost:8082/swagger-ui.html)

### Submitting a Sample Job
You can submit a job using `curl`. We have built-in worker handlers for `email.send`, `report.generate`, `notification.push`, `data.export`, and `test`.

#### ✉️ Sending a Real Email
The `email.send` job type supports sending **actual emails** if you configure SMTP credentials. By default, it will simulate the email, but you can enable real delivery by providing your SMTP details (e.g., Gmail App Password) when starting the services:

```bash
# 1. Start the worker service with SMTP credentials
export SMTP_HOST=smtp.gmail.com
export SMTP_PORT=587
export SMTP_USERNAME=your.email@gmail.com
export SMTP_PASSWORD=your-app-password

./start-all.sh
```

Then, submit the job using the sample script (which will prompt you to edit the `to` field), or use the curl below:

```bash
# 2. Submit a job
JOB_ID=$(curl -s -X POST http://localhost:8081/api/v1/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "type": "email.send",
    "payload": {
      "to": "your-real-email@example.com",
      "subject": "Job scheduler is working",
      "body": "Hello! The distributed job scheduler successfully processed this job from the queue."
    },
    "priority": 1,
    "maxRetries": 3
  }' | grep -o '"jobId":"[^"]*"' | cut -d'"' -f4)

echo "Submitted Job ID: $JOB_ID"

# 3. Check the job status
curl -s "http://localhost:8081/api/v1/jobs/$JOB_ID" | jq .
```

### Checking Worker Status
You can view the active job queue depths and worker metrics:
```bash
curl -s http://localhost:8082/api/v1/worker/status | jq .
```

### Automated Smoke Test
We have included a script that automatically tests the entire flow (health, metrics, idempotency, submission, queue processing):
```bash
cd jobqueue-system
./scripts/smoke-test.sh
```

---

## Health Checks & Metrics

Once running, verify services are up:

```bash
# Submission Service Health & Metrics
curl http://localhost:8081/actuator/health
curl http://localhost:8081/actuator/prometheus

# Worker Service Health & Metrics
curl http://localhost:8082/actuator/health
curl http://localhost:8082/actuator/prometheus
```

---

## Running Tests

```bash
# All tests (requires Docker for integration tests)
mvn clean test

# Specific module
mvn test -pl shared-kernel
mvn test -pl submission-service
mvn test -pl worker-service

# Skip tests (build only)
mvn clean install -DskipTests
```

---

## Implementation Progress

See [`Docs/ProjectTracker.md`](Docs/ProjectTracker.md) for the full week-by-week implementation plan.

| Phase | Status |
|---|---|
| Infrastructure + Docker Compose | ✅ Complete |
| Domain Layer (Job, Priority, Status) | ✅ Complete |
| PostgreSQL Repository (Flyway + JdbcClient) | ✅ Complete |
| Redis Queue Client (Lua scripts) | ✅ Complete |
| Worker Execution Engine | ✅ Complete |
| REST API Controllers | ✅ Complete |
| Observability (Prometheus/Tracing) | ✅ Complete |
| End-to-End Testing | ✅ Complete |

---

## Documentation

| File | Purpose |
|---|---|
| [`Docs/Plan.md`](Docs/Plan.md) | High-level engineering plan and goals |
| [`Docs/Project_architecture.md`](Docs/Project_architecture.md) | Full DDD + HLD + LLD architecture |
| [`Docs/ExecutionPlan.md`](Docs/ExecutionPlan.md) | Week-by-week code implementation plan |
| [`Docs/ProjectTracker.md`](Docs/ProjectTracker.md) | Live feature checklist |
| [`Docs/ProjectCompletion.md`](Docs/ProjectCompletion.md) | Detailed log of all changes made |
| [`Docs/ErrorLog.md`](Docs/ErrorLog.md) | All errors encountered and their resolutions |