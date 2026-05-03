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
