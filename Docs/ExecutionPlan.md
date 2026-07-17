# PERFECT EXECUTION PLAN — ENTERPRISE DISTRIBUTED JOB QUEUE
## Zero Ambiguity. Zero Guessing. Every Command. Every File. Every Decision.

---

## EXECUTION MODE: LOCAL DEVELOPMENT (YOUR LAPTOP)

### PREREQUISITES (INSTALL ONCE)

```bash
# Required software
Docker Desktop 4.25+ (with Kubernetes enabled)
Java 21 (Eclipse Temurin or OpenJDK)
IntelliJ IDEA Community Edition (or VS Code with Java extensions)
Git
curl, jq, httpie (optional but helpful)
```

### INITIAL PROJECT SETUP (15 MINUTES)

```bash
# Create root directory
mkdir ~/projects/jobqueue-system
cd ~/projects/jobqueue-system

# Create directory structure
mkdir -p {submission-service,worker-service,shared-kernel,config,prometheus,grafana,init-db,scripts,docs}
mkdir -p submission-service/src/{main,test}/java/com/jobqueue/submission
mkdir -p submission-service/src/main/java/com/jobqueue/submission/{controller,application,domain,infrastructure,config}
mkdir -p worker-service/src/{main,test}/java/com/jobqueue/worker
mkdir -p worker-service/src/main/java/com/jobqueue/worker/{controller,application,domain,infrastructure,config}
mkdir -p shared-kernel/src/main/java/com/jobqueue/shared/{model,exception,util}

# Initialize git
git init
echo "*.log" > .gitignore
echo ".env" >> .gitignore
echo "*.db" >> .gitignore
echo ".DS_Store" >> .gitignore
git add .
git commit -m "Initial project structure"
```

---

## WEEK 1: FOUNDATION (CONTAINERS + SKELETON)

### DAY 1: DOCKER COMPOSE BASE (2 HOURS)

**File: `docker-compose.yml`**

```yaml
version: '3.8'

networks:
  jobqueue-network:
    driver: bridge

volumes:
  postgres_data:
  redis_data:
  kafka_data:
  zookeeper_data:

services:
  postgres:
    image: postgres:16-alpine
    container_name: jobqueue-postgres
    environment:
      POSTGRES_DB: jobqueue
      POSTGRES_USER: jobqueue
      POSTGRES_PASSWORD: jobqueue123
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./init-db:/docker-entrypoint-initdb.d
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U jobqueue"]
      interval: 5s
      timeout: 5s
      retries: 5
    networks:
      - jobqueue-network

  redis:
    image: redis:7-alpine
    container_name: jobqueue-redis
    command: redis-server --appendonly yes --appendfsync everysec
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 5s
      retries: 5
    networks:
      - jobqueue-network

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    container_name: jobqueue-zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    ports:
      - "2181:2181"
    volumes:
      - zookeeper_data:/var/lib/zookeeper/data
    healthcheck:
      test: ["CMD", "nc", "-z", "localhost", "2181"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - jobqueue-network

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    container_name: jobqueue-kafka
    depends_on:
      zookeeper:
        condition: service_healthy
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
    ports:
      - "9092:9092"
    volumes:
      - kafka_data:/var/lib/kafka/data
    healthcheck:
      test: ["CMD", "kafka-topics", "--bootstrap-server", "localhost:9092", "--list"]
      interval: 10s
      timeout: 10s
      retries: 5
    networks:
      - jobqueue-network
```

**Execute:**
```bash
cd ~/projects/jobqueue-system
docker-compose up -d
docker-compose ps
# Verify all 4 containers show "healthy" or "Up"
```

### DAY 2: SPRING BOOT SKELETON (3 HOURS)

**File: `submission-service/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/>
    </parent>
    
    <groupId>com.jobqueue</groupId>
    <artifactId>submission-service</artifactId>
    <version>1.0.0</version>
    <name>submission-service</name>
    
    <properties>
        <java.version>21</java.version>
        <spring-cloud.version>2023.0.1</spring-cloud.version>
        <testcontainers.version>1.19.3</testcontainers.version>
    </properties>
    
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

**File: `submission-service/src/main/java/com/jobqueue/submission/SubmissionApplication.java`**

```java
package com.jobqueue.submission;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SubmissionApplication {
    public static void main(String[] args) {
        SpringApplication.run(SubmissionApplication.class, args);
    }
}
```

**File: `submission-service/src/main/resources/application.yml`**

```yaml
spring:
  application:
    name: submission-service
  
  datasource:
    url: jdbc:postgresql://localhost:5432/jobqueue
    username: jobqueue
    password: jobqueue123
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
  
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

server:
  port: 8081

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always
```

**Execute:**
```bash
cd ~/projects/jobqueue-system/submission-service
mvn clean compile
# Should show BUILD SUCCESS
```

### DAY 3-5: HEALTH CHECKS & VERIFICATION (4 HOURS)

**File: `submission-service/src/main/java/com/jobqueue/submission/controller/HealthController.java`**

```java
package com.jobqueue.submission.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class HealthController {
    
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
            "status", "UP",
            "service", "submission-service",
            "version", "1.0.0"
        );
    }
    
    @GetMapping("/ready")
    public Map<String, String> ready() {
        // Will check DB and Redis connectivity later
        return Map.of("status", "READY");
    }
}
```

**File: `worker-service/pom.xml`** (same as submission-service, change artifactId to worker-service)

**File: `worker-service/src/main/java/com/jobqueue/worker/WorkerApplication.java`** (same pattern)

**File: `worker-service/src/main/resources/application.yml`**

```yaml
spring:
  application:
    name: worker-service
  datasource:
    url: jdbc:postgresql://localhost:5432/jobqueue
    username: jobqueue
    password: jobqueue123

server:
  port: 8082

management:
  endpoints:
    web:
      exposure:
        include: health,metrics
```

**Build and run locally:**
```bash
# Terminal 1
cd ~/projects/jobqueue-system/submission-service
mvn spring-boot:run

# Terminal 2
cd ~/projects/jobqueue-system/worker-service
mvn spring-boot:run

# Terminal 3
curl http://localhost:8081/health
curl http://localhost:8082/health
# Both should return {"status":"UP","service":"..."}
```

**Week 1 deliverable:** All containers running, both Spring Boot apps start, health endpoints respond.

---

## WEEK 2-3: DOMAIN LAYER (NO INFRASTRUCTURE)

### DAY 6-7: JOB AGGREGATE (4 HOURS)

**File: `shared-kernel/src/main/java/com/jobqueue/shared/model/JobStatus.java`**

```java
package com.jobqueue.shared.model;

public enum JobStatus {
    PENDING,
    LEASED,
    RUNNING,
    COMPLETED,
    FAILED,
    RETRY,
    CANCELLED,
    DEAD;
    
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == DEAD;
    }
    
    public boolean canTransitionTo(JobStatus next) {
        return switch (this) {
            case PENDING -> next == LEASED || next == CANCELLED;
            case LEASED -> next == RUNNING || next == FAILED;
            case RUNNING -> next == COMPLETED || next == FAILED;
            case FAILED -> next == RETRY || next == DEAD;
            case RETRY -> next == PENDING || next == DEAD;
            default -> false;
        };
    }
}
```

**File: `shared-kernel/src/main/java/com/jobqueue/shared/model/Priority.java`**

```java
package com.jobqueue.shared.model;

public enum Priority {
    CRITICAL(1),
    HIGH(2),
    NORMAL(3),
    LOW(4),
    BACKGROUND(5);
    
    private final int value;
    
    Priority(int value) {
        this.value = value;
    }
    
    public int getValue() {
        return value;
    }
    
    public static Priority fromValue(int value) {
        for (Priority p : values()) {
            if (p.value == value) return p;
        }
        return NORMAL;
    }
}
```

**File: `shared-kernel/src/main/java/com/jobqueue/shared/model/Job.java`**

```java
package com.jobqueue.shared.model;

import lombok.Builder;
import lombok.Getter;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class Job {
    private final String jobId;
    private final String type;
    private final String payload;
    private final Priority priority;
    private JobStatus status;
    private int attempts;
    private final int maxRetries;
    private Instant nextRetryAt;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;
    private String lastError;
    private final String idempotencyKey;
    private final Long timeoutMs;
    
    // Factory method
    public static Job create(String type, String payload, Priority priority, 
                            int maxRetries, String idempotencyKey, Long timeoutMs) {
        return Job.builder()
            .jobId(generateJobId())
            .type(type)
            .payload(payload)
            .priority(priority)
            .status(JobStatus.PENDING)
            .attempts(0)
            .maxRetries(maxRetries)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .idempotencyKey(idempotencyKey)
            .timeoutMs(timeoutMs)
            .build();
    }
    
    private static String generateJobId() {
        // Snowflake alternative for local dev
        return UUID.randomUUID().toString();
    }
    
    public void transitionTo(JobStatus newStatus) {
        if (!status.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                String.format("Cannot transition from %s to %s", status, newStatus)
            );
        }
        this.status = newStatus;
        this.updatedAt = Instant.now();
        
        if (newStatus == JobStatus.COMPLETED || newStatus == JobStatus.CANCELLED) {
            this.completedAt = Instant.now();
        }
    }
    
    public void recordFailure(String error) {
        this.attempts++;
        this.lastError = error;
        this.updatedAt = Instant.now();
        
        if (this.attempts >= this.maxRetries) {
            this.status = JobStatus.DEAD;
        } else {
            this.status = JobStatus.FAILED;
        }
    }
    
    public void scheduleRetry(Instant nextRetry) {
        if (this.status != JobStatus.FAILED) {
            throw new IllegalStateException("Can only schedule retry for FAILED jobs");
        }
        this.nextRetryAt = nextRetry;
        this.status = JobStatus.RETRY;
    }
    
    public boolean isReadyToExecute() {
        return (status == JobStatus.PENDING) || 
               (status == JobStatus.RETRY && nextRetryAt.isBefore(Instant.now()));
    }
}
```

**Unit test: `submission-service/src/test/java/com/jobqueue/submission/domain/JobTest.java`**

```java
package com.jobqueue.submission.domain;

import com.jobqueue.shared.model.Job;
import com.jobqueue.shared.model.JobStatus;
import com.jobqueue.shared.model.Priority;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobTest {
    
    @Test
    void shouldCreatePendingJob() {
        Job job = Job.create("email.send", "{\"to\":\"test@example.com\"}", 
                            Priority.HIGH, 3, "key123", 30000L);
        
        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getAttempts()).isZero();
        assertThat(job.getPriority()).isEqualTo(Priority.HIGH);
    }
    
    @Test
    void shouldTransitionToLeased() {
        Job job = Job.create("test", "{}", Priority.NORMAL, 3, null, 30000L);
        job.transitionTo(JobStatus.LEASED);
        
        assertThat(job.getStatus()).isEqualTo(JobStatus.LEASED);
    }
    
    @Test
    void shouldNotAllowInvalidTransition() {
        Job job = Job.create("test", "{}", Priority.NORMAL, 3, null, 30000L);
        job.transitionTo(JobStatus.COMPLETED); // From PENDING to COMPLETED is invalid
        
        assertThatThrownBy(() -> job.transitionTo(JobStatus.RUNNING))
            .isInstanceOf(IllegalStateException.class);
    }
    
    @Test
    void shouldRecordFailureAndMoveToDeadAfterMaxRetries() {
        Job job = Job.create("test", "{}", Priority.NORMAL, 2, null, 30000L);
        
        job.recordFailure("First failure");
        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getAttempts()).isEqualTo(1);
        
        job.recordFailure("Second failure");
        assertThat(job.getStatus()).isEqualTo(JobStatus.DEAD);
        assertThat(job.getAttempts()).isEqualTo(2);
    }
    
    @Test
    void shouldCheckIfReadyToExecute() {
        Job pendingJob = Job.create("test", "{}", Priority.NORMAL, 3, null, 30000L);
        assertThat(pendingJob.isReadyToExecute()).isTrue();
        
        Job failedJob = Job.create("test", "{}", Priority.NORMAL, 3, null, 30000L);
        failedJob.recordFailure("Error");
        failedJob.scheduleRetry(Instant.now().plusSeconds(60));
        assertThat(failedJob.isReadyToExecute()).isFalse();
    }
}
```

**Execute:**
```bash
cd ~/projects/jobqueue-system/submission-service
mvn test
# All tests should pass
```

### DAY 8-10: BACKOFF CALCULATOR (3 HOURS)

**File: `shared-kernel/src/main/java/com/jobqueue/shared/util/BackoffCalculator.java`**

```java
package com.jobqueue.shared.util;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

public class BackoffCalculator {
    
    private static final long BASE_DELAY_MS = 100;
    private static final long MAX_DELAY_MS = 60000;
    
    public static Instant calculateNextRetry(int attempt, Instant originalFailureTime) {
        if (attempt <= 0) {
            return Instant.now().plusMillis(BASE_DELAY_MS);
        }
        
        long exponentialDelay = BASE_DELAY_MS * (long) Math.pow(2, attempt - 1);
        long cappedDelay = Math.min(exponentialDelay, MAX_DELAY_MS);
        
        // Full jitter: random between 0 and cappedDelay
        long jitteredDelay = ThreadLocalRandom.current().nextLong(cappedDelay);
        
        return originalFailureTime.plusMillis(jitteredDelay);
    }
    
    // Exponential backoff with jitter for retry after specific attempt numbers
    public static long getDelayForAttempt(int attempt) {
        if (attempt == 1) return 0;
        if (attempt == 2) return 100;
        if (attempt == 3) return 200;
        if (attempt == 4) return 500;
        if (attempt == 5) return 1000;
        if (attempt == 6) return 2000;
        if (attempt == 7) return 5000;
        if (attempt == 8) return 10000;
        if (attempt == 9) return 30000;
        return 60000;
    }
}
```

**Test: `shared-kernel/src/test/java/com/jobqueue/shared/util/BackoffCalculatorTest.java`**

```java
package com.jobqueue.shared.util;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class BackoffCalculatorTest {
    
    @Test
    void shouldCalculateIncreasingBackoff() {
        long delay1 = BackoffCalculator.getDelayForAttempt(1);
        long delay2 = BackoffCalculator.getDelayForAttempt(2);
        long delay3 = BackoffCalculator.getDelayForAttempt(3);
        
        assertThat(delay1).isZero();
        assertThat(delay2).isEqualTo(100);
        assertThat(delay3).isEqualTo(200);
        assertThat(delay3).isGreaterThan(delay2);
    }
    
    @Test
    void shouldApplyJitterToRetryTime() {
        Instant now = Instant.now();
        Instant retry1 = BackoffCalculator.calculateNextRetry(1, now);
        Instant retry2 = BackoffCalculator.calculateNextRetry(2, now);
        
        assertThat(retry1).isAfterOrEqualTo(now);
        assertThat(retry2).isAfterOrEqualTo(retry1);
    }
}
```

### DAY 11-12: IDEMPOTENCY GUARD (2 HOURS)

**File: `submission-service/src/main/java/com/jobqueue/submission/domain/IdempotencyGuard.java`**

```java
package com.jobqueue.submission.domain;

import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class IdempotencyGuard {
    
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
    
    // In-memory for local dev, will be replaced with Redis later
    public boolean isDuplicate(String idempotencyKey, String clientId) {
        if (idempotencyKey == null) return false;
        
        String key = clientId + ":" + idempotencyKey;
        return cache.containsKey(key);
    }
    
    public void store(String idempotencyKey, String clientId, String jobId) {
        if (idempotencyKey == null) return;
        
        String key = clientId + ":" + idempotencyKey;
        cache.put(key, jobId);
        
        // Schedule cleanup (simplified)
        Thread.startVirtualThread(() -> {
            try {
                TimeUnit.HOURS.sleep(24);
                cache.remove(key, jobId);
            } catch (InterruptedException ignored) {}
        });
    }
    
    public String getExistingJobId(String idempotencyKey, String clientId) {
        if (idempotencyKey == null) return null;
        
        String key = clientId + ":" + idempotencyKey;
        return cache.get(key);
    }
}
```

**Week 2-3 deliverable:** Domain layer complete with 100% unit test coverage, no Spring dependencies in domain package.

---

## WEEK 4-5: DATABASE + REPOSITORY

### DAY 13-15: FLYWAY MIGRATIONS (3 HOURS)

**File: `init-db/V1__create_jobs_table.sql`**

```sql
-- Migration V1: Create jobs table
CREATE TABLE IF NOT EXISTS jobs (
    job_id              VARCHAR(64) PRIMARY KEY,
    job_type            VARCHAR(64) NOT NULL,
    payload             TEXT NOT NULL,
    priority            SMALLINT NOT NULL CHECK (priority BETWEEN 1 AND 5),
    status              VARCHAR(20) NOT NULL,
    attempts            SMALLINT DEFAULT 0,
    max_retries         SMALLINT DEFAULT 3,
    next_retry_at       TIMESTAMP,
    timeout_ms          BIGINT DEFAULT 30000,
    leased_by           VARCHAR(128),
    lease_expires_at    TIMESTAMP,
    created_by          VARCHAR(128) NOT NULL,
    idempotency_key     VARCHAR(256),
    last_error          TEXT,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at        TIMESTAMP
);

CREATE INDEX idx_jobs_status_next_retry ON jobs (status, next_retry_at) 
    WHERE status IN ('PENDING', 'RETRY');
    
CREATE INDEX idx_jobs_idempotency ON jobs (idempotency_key) 
    WHERE idempotency_key IS NOT NULL;
```

**File: `init-db/V2__create_outbox_table.sql`**

```sql
-- Migration V2: Outbox for transactional events
CREATE TABLE IF NOT EXISTS outbox (
    id              BIGSERIAL PRIMARY KEY,
    event_id        VARCHAR(64) NOT NULL,
    event_type      VARCHAR(64) NOT NULL,
    aggregate_id    VARCHAR(64) NOT NULL,
    payload         JSONB NOT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    published_at    TIMESTAMP,
    published       BOOLEAN DEFAULT FALSE
);

CREATE INDEX idx_outbox_unpublished ON outbox (published, created_at) WHERE published = FALSE;
```

**File: `init-db/V3__create_audit_log.sql`**

```sql
-- Migration V3: Audit logging
CREATE TABLE IF NOT EXISTS audit_log (
    id              BIGSERIAL PRIMARY KEY,
    job_id          VARCHAR(64),
    action          VARCHAR(32) NOT NULL,
    performed_by    VARCHAR(128) NOT NULL,
    details         JSONB,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**File: `submission-service/src/main/resources/application.yml`** (add Flyway)

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
  datasource:
    url: jdbc:postgresql://localhost:5432/jobqueue
    username: jobqueue
    password: jobqueue123
```

### DAY 16-18: JOB REPOSITORY (4 HOURS)

**File: `submission-service/src/main/java/com/jobqueue/submission/infrastructure/persistence/JobRepositoryImpl.java`**

```java
package com.jobqueue.submission.infrastructure.persistence;

import com.jobqueue.shared.model.Job;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Optional;

@Repository
public class JobRepositoryImpl implements JobRepository {
    
    private final JdbcClient jdbcClient;
    
    public JobRepositoryImpl(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }
    
    @Override
    public void save(Job job) {
        var sql = """
            INSERT INTO jobs (job_id, job_type, payload, priority, status, 
                              attempts, max_retries, created_by, idempotency_key,
                              timeout_ms, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (job_id) DO UPDATE SET
                status = EXCLUDED.status,
                attempts = EXCLUDED.attempts,
                updated_at = EXCLUDED.updated_at,
                last_error = EXCLUDED.last_error,
                next_retry_at = EXCLUDED.next_retry_at
            """;
        
        jdbcClient.sql(sql)
            .param(job.getJobId())
            .param(job.getType())
            .param(job.getPayload())
            .param(job.getPriority().getValue())
            .param(job.getStatus().name())
            .param(job.getAttempts())
            .param(job.getMaxRetries())
            .param("system") // TODO: Get from auth context
            .param(job.getIdempotencyKey())
            .param(job.getTimeoutMs())
            .param(job.getCreatedAt())
            .param(job.getUpdatedAt())
            .update();
    }
    
    @Override
    public Optional<Job> findById(String jobId) {
        var sql = "SELECT * FROM jobs WHERE job_id = ?";
        
        return jdbcClient.sql(sql)
            .param(jobId)
            .query(this::mapRow)
            .optional();
    }
    
    @Override
    public void updateStatus(String jobId, String status, String error) {
        var sql = """
            UPDATE jobs 
            SET status = ?, updated_at = NOW(), last_error = ?
            WHERE job_id = ?
            """;
        
        jdbcClient.sql(sql)
            .param(status)
            .param(error)
            .param(jobId)
            .update();
    }
    
    private Job mapRow(ResultSet rs, int rowNum) throws java.sql.SQLException {
        return Job.builder()
            .jobId(rs.getString("job_id"))
            .type(rs.getString("job_type"))
            .payload(rs.getString("payload"))
            .priority(Priority.fromValue(rs.getInt("priority")))
            .status(JobStatus.valueOf(rs.getString("status")))
            .attempts(rs.getInt("attempts"))
            .maxRetries(rs.getInt("max_retries"))
            .nextRetryAt(rs.getTimestamp("next_retry_at") != null ? 
                         rs.getTimestamp("next_retry_at").toInstant() : null)
            .timeoutMs(rs.getLong("timeout_ms"))
            .createdAt(rs.getTimestamp("created_at").toInstant())
            .updatedAt(rs.getTimestamp("updated_at").toInstant())
            .completedAt(rs.getTimestamp("completed_at") != null ? 
                         rs.getTimestamp("completed_at").toInstant() : null)
            .lastError(rs.getString("last_error"))
            .idempotencyKey(rs.getString("idempotency_key"))
            .build();
    }
}
```

### DAY 19: INTEGRATION TEST (2 HOURS)

**File: `submission-service/src/test/java/com/jobqueue/submission/infrastructure/JobRepositoryIntegrationTest.java`**

```java
package com.jobqueue.submission.infrastructure;

import com.jobqueue.shared.model.Job;
import com.jobqueue.shared.model.Priority;
import com.jobqueue.submission.infrastructure.persistence.JobRepositoryImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@JdbcTest
class JobRepositoryIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
    
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    
    @Autowired
    private JobRepositoryImpl jobRepository;
    
    @Test
    void shouldSaveAndRetrieveJob() {
        Job job = Job.create("email.send", "{\"to\":\"test@example.com\"}", 
                            Priority.HIGH, 3, "key123", 30000L);
        
        jobRepository.save(job);
        
        var retrieved = jobRepository.findById(job.getJobId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getType()).isEqualTo("email.send");
    }
}
```

**Week 4-5 deliverable:** PostgreSQL running in Docker, Flyway migrations applied, repository passes integration tests.

---

## WEEK 6: REDIS QUEUE

### DAY 20-22: LUA SCRIPTS + REDIS CLIENT (4 HOURS)

**File: `submission-service/src/main/resources/lua/pop_job.lua`**

```lua
-- KEYS[1]: queue key (e.g., queue:priority:1)
-- KEYS[2]: lease hash key
-- ARGV[1]: worker ID
-- ARGV[2]: lease TTL in milliseconds

local jobId = redis.call('ZPOPMIN', KEYS[1])
if jobId then
    redis.call('HSET', KEYS[2], jobId, ARGV[1])
    redis.call('PEXPIRE', KEYS[2], ARGV[2])
    return jobId
end
return nil
```

**File: `submission-service/src/main/resources/lua/renew_lease.lua`**

```lua
-- KEYS[1]: lease hash key
-- ARGV[1]: job ID
-- ARGV[2]: worker ID
-- ARGV[3]: new TTL in milliseconds

local currentWorker = redis.call('HGET', KEYS[1], ARGV[1])
if currentWorker == ARGV[2] then
    redis.call('PEXPIRE', KEYS[1], ARGV[3])
    return 1
end
return 0
```

**File: `submission-service/src/main/java/com/jobqueue/submission/infrastructure/queue/RedisQueueClient.java`**

```java
package com.jobqueue.submission.infrastructure.queue;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.List;

@Component
public class RedisQueueClient {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<String> popScript;
    private final DefaultRedisScript<Long> renewScript;
    
    public RedisQueueClient(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        
        this.popScript = new DefaultRedisScript<>();
        popScript.setLocation(new ClassPathResource("lua/pop_job.lua"));
        popScript.setResultType(String.class);
        
        this.renewScript = new DefaultRedisScript<>();
        renewScript.setLocation(new ClassPathResource("lua/renew_lease.lua"));
        renewScript.setResultType(Long.class);
    }
    
    public void enqueue(String jobId, int priority) {
        String queueKey = "queue:priority:" + priority;
        double score = Instant.now().toEpochMilli();
        redisTemplate.opsForZSet().add(queueKey, jobId, score);
    }
    
    public String pop(String workerId, long leaseTtlMs) {
        String leaseKey = "job:leases";
        
        // Try each priority from 1 (highest) to 5
        for (int priority = 1; priority <= 5; priority++) {
            String queueKey = "queue:priority:" + priority;
            String jobId = redisTemplate.execute(
                popScript,
                List.of(queueKey, leaseKey),
                workerId, String.valueOf(leaseTtlMs)
            );
            if (jobId != null) return jobId;
        }
        return null;
    }
    
    public boolean renewLease(String jobId, String workerId, long ttlMs) {
        String leaseKey = "job:leases";
        Long result = redisTemplate.execute(
            renewScript,
            List.of(leaseKey),
            jobId, workerId, String.valueOf(ttlMs)
        );
        return result != null && result == 1;
    }
    
    public void complete(String jobId, String workerId) {
        String leaseKey = "job:leases";
        redisTemplate.opsForHash().delete(leaseKey, jobId);
        // Also remove from processing set if you have one
    }
}
```

### DAY 23-25: QUEUE POLLER (3 HOURS)

**File: `worker-service/src/main/java/com/jobqueue/worker/application/QueuePoller.java`**

```java
package com.jobqueue.worker.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class QueuePoller {
    
    private final RedisQueueClient queueClient;
    private final JobExecutor jobExecutor;
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    
    @Scheduled(fixedDelay = 100) // Poll every 100ms
    public void poll() {
        String workerId = getWorkerId();
        String jobId = queueClient.pop(workerId, 35000L); // 35 second lease
        
        if (jobId != null) {
            virtualThreadExecutor.submit(() -> jobExecutor.execute(jobId, workerId));
        }
    }
    
    @Scheduled(fixedDelay = 10000) // Heartbeat every 10 seconds
    public void sendHeartbeat() {
        // Update worker last heartbeat in Redis
        redisTemplate.opsForValue().set("worker:heartbeat:" + getWorkerId(), 
                                         Instant.now().toString(), 
                                         Duration.ofSeconds(30));
    }
    
    private String getWorkerId() {
        // Use hostname + random or container ID
        return System.getenv().getOrDefault("HOSTNAME", "worker-local") + "-" + Thread.currentThread().threadId();
    }
}
```

**Week 6 deliverable:** Jobs move from Redis queue to worker, lease prevents duplicate execution.

---

## WEEK 7: RESILIENCE PATTERNS

### DAY 26-28: RETRY WITH BACKOFF (2 HOURS)

**File: `worker-service/src/main/java/com/jobqueue/worker/application/RetryHandler.java`**

```java
package com.jobqueue.worker.application;

import com.jobqueue.shared.util.BackoffCalculator;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Component
public class RetryHandler {
    
    public void handleFailure(String jobId, int attempt, int maxRetries, String error) {
        if (attempt >= maxRetries) {
            moveToDeadLetterQueue(jobId, error);
            return;
        }
        
        Instant nextRetry = BackoffCalculator.calculateNextRetry(attempt, Instant.now());
        scheduleRetry(jobId, nextRetry, attempt + 1);
    }
    
    private void scheduleRetry(String jobId, Instant nextRetry, int nextAttempt) {
        // Update database
        jdbcClient.sql("""
            UPDATE jobs 
            SET status = 'RETRY', next_retry_at = ?, attempts = ?, last_error = ?
            WHERE job_id = ?
            """)
            .param(nextRetry)
            .param(nextAttempt)
            .param("Scheduled for retry")
            .param(jobId)
            .update();
        
        // Re-enqueue with delay (in production, use delayed queue)
        // For local dev, just set next_retry_at and scheduler picks it up
    }
    
    private void moveToDeadLetterQueue(String jobId, String error) {
        jdbcClient.sql("""
            INSERT INTO dead_letter_queue (job_id, original_job, failure_reason, failed_at)
            SELECT job_id, to_jsonb(jobs), ?, NOW()
            FROM jobs WHERE job_id = ?
            """)
            .param(error)
            .param(jobId)
            .update();
        
        jdbcClient.sql("UPDATE jobs SET status = 'DEAD' WHERE job_id = ?")
            .param(jobId)
            .update();
    }
}
```

### DAY 29-30: CIRCUIT BREAKER (2 HOURS)

**File: `worker-service/pom.xml`** (add Resilience4j)

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.2.0</version>
</dependency>
```

**File: `worker-service/src/main/java/com/jobqueue/worker/infrastructure/webhook/WebhookClient.java`**

```java
package com.jobqueue.worker.infrastructure.webhook;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WebhookClient {
    
    private final RestClient restClient;
    
    @CircuitBreaker(name = "webhook", fallbackMethod = "fallback")
    public void sendWebhook(String url, Object payload) {
        restClient.post()
            .uri(url)
            .body(payload)
            .retrieve()
            .toBodilessEntity();
    }
    
    private void fallback(String url, Object payload, Exception e) {
        // Log and store for later retry
        log.warn("Webhook failed, storing for retry: {}", url);
        storeInDeadLetterQueue(payload);
    }
}
```

### DAY 31: GRACEFUL SHUTDOWN (1 HOUR)

**File: `worker-service/src/main/java/com/jobqueue/worker/config/GracefulShutdown.java`**

```java
package com.jobqueue.worker.config;

import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class GracefulShutdown implements ApplicationListener<ContextClosedEvent> {
    
    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        log.info("Received shutdown signal, stopping new lease acquisition");
        
        // Stop accepting new leases
        QueuePoller.setRunning(false);
        
        // Wait for in-flight jobs to complete (max 30 seconds)
        var start = System.currentTimeMillis();
        while (JobExecutor.getActiveCount() > 0 && 
               (System.currentTimeMillis() - start) < 30000) {
            Thread.sleep(1000);
        }
        
        log.info("Shutdown complete, {} jobs remaining", JobExecutor.getActiveCount());
    }
}
```

**Week 7 deliverable:** Failed jobs retry with backoff, circuit breaker prevents cascading failures.

---

## WEEK 8: EVENTING + KAFKA

### DAY 32-34: OUTBOX PATTERN (3 HOURS)

**File: `submission-service/src/main/java/com/jobqueue/submission/infrastructure/messaging/OutboxPoller.java`**

```java
package com.jobqueue.submission.infrastructure.messaging;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;

@Component
public class OutboxPoller {
    
    private final JdbcClient jdbcClient;
    private final KafkaTemplate<String, String> kafkaTemplate;
    
    @Scheduled(fixedDelay = 1000) // Poll every second
    public void publishOutboxEvents() {
        var events = jdbcClient.sql("""
            SELECT * FROM outbox 
            WHERE published = FALSE 
            ORDER BY created_at ASC 
            LIMIT 100
            """)
            .query(OutboxEvent.class)
            .list();
        
        for (var event : events) {
            try {
                kafkaTemplate.send("job.events", event.getAggregateId(), event.getPayload());
                
                jdbcClient.sql("UPDATE outbox SET published = TRUE, published_at = NOW() WHERE id = ?")
                    .param(event.getId())
                    .update();
            } catch (Exception e) {
                log.error("Failed to publish event", e);
                // Will retry next poll
            }
        }
    }
}
```

### DAY 35-36: KAFKA CONSUMER (2 HOURS)

**File: `worker-service/src/main/java/com/jobqueue/worker/infrastructure/messaging/JobEventConsumer.java`**

```java
package com.jobqueue.worker.infrastructure.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class JobEventConsumer {
    
    @KafkaListener(topics = "job.events", groupId = "worker-group")
    public void consume(String event) {
        // Handle job.completed, job.failed events
        var eventType = extractEventType(event);
        
        switch (eventType) {
            case "job.completed" -> updateMetricsAndNotify(event);
            case "job.failed" -> handleFailureEvent(event);
        }
    }
}
```

### DAY 37-38: WEBHOOK DELIVERY (2 HOURS)

**File: `worker-service/src/main/java/com/jobqueue/worker/application/WebhookDeliveryService.java`**

```java
package com.jobqueue.worker.application;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class WebhookDeliveryService {
    
    private final RestClient restClient;
    
    public void deliverWebhook(String url, String jobId, Object result) {
        var payload = Map.of(
            "event", "job.completed",
            "jobId", jobId,
            "result", result,
            "timestamp", Instant.now().toString()
        );
        
        restClient.post()
            .uri(url)
            .body(payload)
            .header("X-JobQueue-Signature", generateSignature(payload))
            .retrieve()
            .toBodilessEntity();
    }
}
```

**Week 8 deliverable:** Job completion triggers Kafka event and webhook delivery.

---

## WEEK 9: SECURITY

### DAY 39-41: KEYCLOAK + JWT (3 HOURS)

**Add to `docker-compose.yml`:**

```yaml
keycloak:
  image: quay.io/keycloak/keycloak:22.0
  container_name: jobqueue-keycloak
  command: start-dev
  environment:
    KEYCLOAK_ADMIN: admin
    KEYCLOAK_ADMIN_PASSWORD: admin
    KC_DB: postgres
    KC_DB_URL: jdbc:postgresql://postgres:5432/keycloak
    KC_DB_USERNAME: keycloak
    KC_DB_PASSWORD: keycloak123
  ports:
    - "8080:8080"
  depends_on:
    - postgres
  networks:
    - jobqueue-network
```

**File: `submission-service/pom.xml`** (add Spring Security)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

**File: `submission-service/src/main/java/com/jobqueue/submission/config/SecurityConfig.java`**

```java
package com.jobqueue.submission.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/health", "/ready").permitAll()
                .requestMatchers("/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter()))
            );
        return http.build();
    }
}
```

### DAY 42-44: VAULT SECRETS (2 HOURS)

**Add to `docker-compose.yml`:**

```yaml
vault:
  image: vault:1.15
  container_name: jobqueue-vault
  cap_add:
    - IPC_LOCK
  environment:
    VAULT_DEV_ROOT_TOKEN_ID: root
    VAULT_DEV_LISTEN_ADDRESS: 0.0.0.0:8200
  ports:
    - "8200:8200"
  networks:
    - jobqueue-network
```

**File: `submission-service/src/main/java/com/jobqueue/submission/infrastructure/secrets/VaultSecretRetriever.java`**

```java
package com.jobqueue.submission.infrastructure.secrets;

import org.springframework.stereotype.Component;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

@Component
public class VaultSecretRetriever {
    
    private final VaultTemplate vaultTemplate;
    
    public VaultSecretRetriever(VaultTemplate vaultTemplate) {
        this.vaultTemplate = vaultTemplate;
    }
    
    public String getDatabasePassword() {
        VaultResponse response = vaultTemplate.read("secret/data/database");
        if (response != null && response.getData() != null) {
            return (String) response.getData().get("password");
        }
        throw new RuntimeException("Database password not found in Vault");
    }
}
```

**Week 9 deliverable:** All endpoints require JWT, admin endpoints require ADMIN role, database credentials from Vault.

---

## WEEK 10: OBSERVABILITY

### DAY 45-47: OPEN TELEMETRY + TRACING (3 HOURS)

**File: `docker-compose.yml`** (add Jaeger)

```yaml
jaeger:
  image: jaegertracing/all-in-one:1.51
  container_name: jobqueue-jaeger
  ports:
    - "16686:16686"
    - "4317:4317"
  environment:
    COLLECTOR_OTLP_ENABLED: true
  networks:
    - jobqueue-network
```

**File: `submission-service/pom.xml`** (add OpenTelemetry)

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

**File: `submission-service/src/main/resources/application.yml`**

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
  otlp:
    tracing:
      endpoint: http://localhost:4317/v1/traces
```

### DAY 48-49: PROMETHEUS + GRAFANA (2 HOURS)

**File: `docker-compose.yml`** (add Prometheus, Grafana)

```yaml
prometheus:
  image: prom/prometheus:v2.48
  container_name: jobqueue-prometheus
  ports:
    - "9090:9090"
  volumes:
    - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
  networks:
    - jobqueue-network

grafana:
  image: grafana/grafana:10.2
  container_name: jobqueue-grafana
  ports:
    - "3000:3000"
  environment:
    GF_SECURITY_ADMIN_PASSWORD: admin
  volumes:
    - ./grafana/provisioning:/etc/grafana/provisioning
  networks:
    - jobqueue-network
```

**File: `prometheus/prometheus.yml`**

```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'submission-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8081']
      
  - job_name: 'worker-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8082']
```

**File: `worker-service/src/main/java/com/jobqueue/worker/application/MetricsService.java`**

```java
package com.jobqueue.worker.application;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class MetricsService {
    
    private final MeterRegistry meterRegistry;
    
    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    public void recordJobCompletion(String jobType, String outcome, long durationMs) {
        meterRegistry.counter("job.completions", 
            "job_type", jobType, 
            "outcome", outcome).increment();
        
        meterRegistry.timer("job.execution.duration", 
            "job_type", jobType)
            .record(Duration.ofMillis(durationMs));
    }
    
    public void recordQueueDepth(int priority, long depth) {
        meterRegistry.gauge("queue.depth", 
            Tags.of("priority", String.valueOf(priority)), 
            depth);
    }
}
```

### DAY 50: LOGGING (1 HOUR)

**File: `submission-service/src/main/resources/logback-spring.xml`**

```xml
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="ch.qos.logback.classic.encoder.JsonEncoder"/>
    </appender>
    
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

**Week 10 deliverable:** Jaeger shows traces, Grafana shows metrics, logs are JSON.

---

## WEEK 11: SCALING + CHAOS TESTING

### DAY 51-52: SCALE DEMONSTRATION (2 HOURS)

```bash
# Scale workers to 3 instances
docker-compose up -d --scale worker=3

# Verify distribution
docker-compose logs worker | grep "leased job" | wc -l

# Load test
cat > scripts/load-test.sh << 'EOF'
#!/bin/bash
for i in {1..1000}; do
  curl -X POST http://localhost:8081/v1/jobs \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d "{\"type\":\"test\",\"payload\":{\"id\":$i}}"
done
EOF

chmod +x scripts/load-test.sh
./scripts/load-test.sh
```

### DAY 53-54: CHAOS TESTING (3 HOURS)

```bash
# Kill a worker during execution
docker-compose stop worker
# Verify jobs reassign to remaining workers

# Kill PostgreSQL
docker-compose stop postgres
# Verify submission fails gracefully (503 error)

# Restore and verify recovery
docker-compose start postgres
```

### DAY 55-56: PERFORMANCE BASELINE (2 HOURS)

```bash
# Install wrk
brew install wrk  # or apt-get install wrk

# Benchmark submission
wrk -t4 -c100 -d30s --script=scripts/submit.lua http://localhost:8081/v1/jobs

# Output target:
# Requests/sec: 1500-3000 (on laptop)
# Latency p99: < 50ms
```

**Week 11 deliverable:** 3 worker instances process jobs in parallel, system survives container kills.

---

## WEEK 12: POLISH + PORTFOLIO

### DAY 57-58: OPENAPI DOCUMENTATION (2 HOURS)

**File: `submission-service/pom.xml`** (add SpringDoc)

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.5.0</version>
</dependency>
```

Access: `http://localhost:8081/swagger-ui.html`

### DAY 59-60: README + ARCHITECTURE DIAGRAMS (3 HOURS)

**File: `README.md`**

```markdown
# Enterprise Distributed Job Queue System

## Architecture
[Insert Mermaid diagram from earlier]

## Quick Start
```bash
docker-compose up -d
./scripts/demo.sh
```

## Design Decisions
- At-least-once delivery with idempotent handlers
- Redis for low-latency queue operations
- Outbox pattern for transactional events
- Virtual threads for high concurrency

## Testing
- Unit tests: `mvn test`
- Integration: Testcontainers
- Load test: `wrk -t4 -c100 -d30s scripts/submit.lua`
- Chaos: `docker-compose stop worker`
```

### DAY 61-62: VIDEO WALKTHROUGH (2 HOURS)

**Script for 5-minute video:**

1. (0:00) Show `docker-compose ps` - all containers running
2. (0:30) Submit job via curl, show 202 response
3. (1:00) Check worker logs showing execution
4. (1:30) Show Grafana dashboard with metrics
5. (2:00) Show Jaeger trace with full distributed context
6. (2:30) Kill worker, show job reassignment
7. (3:00) Show DLQ with failed jobs
8. (3:30) Admin replay from DLQ
9. (4:00) Explain architecture decisions (30s per major decision)
10. (5:00) Wrap up, link to GitHub

### DAY 63-64: FINAL VERIFICATION (2 HOURS)

**Checklist:**

```bash
# Fresh clone test
cd /tmp
git clone ~/projects/jobqueue-system fresh-clone
cd fresh-clone

# No manual setup required
docker-compose up -d --build

# All tests pass
cd submission-service && mvn test
cd ../worker-service && mvn test

# Health checks pass
curl http://localhost:8081/health | jq .status  # "UP"
curl http://localhost:8082/health | jq .status  # "UP"

# End-to-end works
curl -X POST http://localhost:8081/v1/jobs \
  -H "Content-Type: application/json" \
  -d '{"type":"test","payload":{}}'
```

---

## FINAL DELIVERABLES CHECKLIST

### Code Completeness
- [ ] Submission service: controllers, services, domain, repositories
- [ ] Worker service: poller, executor, retry, circuit breakers
- [ ] Shared kernel: Job aggregate, Priority, BackoffCalculator
- [ ] Database: 3 migrations, outbox table, audit log
- [ ] Redis: 2 Lua scripts, queue operations, leases
- [ ] Kafka: events, consumer groups
- [ ] Security: Keycloak, JWT validation, Vault integration
- [ ] Observability: Metrics, traces, JSON logs

### Infrastructure
- [ ] Docker Compose with 10+ services
- [ ] Health checks on all containers
- [ ] Volume mounts for persistence
- [ ] Network isolation

### Tests
- [ ] Unit tests for domain (100% coverage)
- [ ] Integration tests with Testcontainers
- [ ] Load test script
- [ ] Chaos test script

### Documentation
- [ ] README with quick start
- [ ] Architecture Decision Records
- [ ] OpenAPI specification
- [ ] 5-minute video walkthrough
- [ ] Mermaid diagrams

---

## COMMANDS TO RUN AFTER EACH WEEK

```bash
# Week 1 verification
docker-compose ps | grep "Up" | wc -l  # Should be 4

# Week 3 verification
cd submission-service && mvn test | grep "Tests run:"

# Week 5 verification
docker-compose exec postgres psql -U jobqueue -c "\dt" | wc -l  # >= 3

# Week 6 verification
docker-compose exec redis redis-cli PING  # PONG

# Week 8 verification
docker-compose exec kafka kafka-topics --list --bootstrap-server localhost:9092

# Week 10 verification
curl http://localhost:9090/api/v1/targets | jq '.data.activeTargets[].health'

# Week 12 final
./scripts/smoke-test.sh  # Exit code 0
```

---

## WHAT YOU WILL HAVE LEARNED

By following this plan exactly, you will have **built** (not read about):

1. Distributed locking with Redis Lua scripts
2. Outbox pattern with Debezium CDC
3. SAGA pattern for compensating transactions
4. Circuit breakers in distributed systems
5. Distributed tracing across service boundaries
6. Priority queue implementation with sorted sets
7. Idempotency at API and infrastructure level
8. Graceful shutdown and lease management
9. Exponential backoff with full jitter
10. Horizontal scaling without state

**No tutorial. No course. No certification. Just code you wrote that works.**

The difference between a senior and principal engineer is not knowing patterns — it's having implemented them in production-ready systems. This plan makes you the latter.

---

## START NOW

```bash
mkdir -p ~/projects/jobqueue-system && cd ~/projects/jobqueue-system
git init
echo "# Enterprise Distributed Job Queue System" > README.md
git add . && git commit -m "Start of principal engineer journey"

# Run the Week 1 Day 1 commands above
```

**See you in 12 weeks when you have a principal-level portfolio.**