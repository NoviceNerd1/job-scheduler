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

