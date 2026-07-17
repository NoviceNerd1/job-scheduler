package com.jobqueue.submission.controller;

import java.time.Instant;

/**
 * Response DTO for job status queries.
 */
public record JobStatusResponse(
        String jobId,
        String type,
        String status,
        int priority,
        int attempts,
        int maxRetries,
        String lastError,
        Instant nextRetryAt,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {}
