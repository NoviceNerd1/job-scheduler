package com.jobqueue.submission.controller;

import java.time.Instant;

/**
 * Response DTO returned after a job is successfully accepted.
 */
public record JobSubmitResponse(
        String jobId,
        String type,
        String status,
        int priority,
        Instant submittedAt,
        String message
) {}
