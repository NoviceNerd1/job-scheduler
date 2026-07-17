package com.jobqueue.submission.controller;

import jakarta.validation.constraints.*;
import java.util.Map;

/**
 * Request DTO for submitting a job via POST /api/v1/jobs
 */
public record JobSubmitRequest(

        @NotBlank(message = "Job type must not be blank")
        @Size(max = 100, message = "Job type must not exceed 100 characters")
        String type,

        @NotNull(message = "Payload must not be null")
        Map<String, Object> payload,

        /**
         * Priority 1 (highest) to 5 (lowest). Defaults to 3 (NORMAL).
         */
        @Min(value = 1, message = "Priority must be between 1 and 5")
        @Max(value = 5, message = "Priority must be between 1 and 5")
        Integer priority,

        /**
         * Max number of retry attempts before the job moves to DEAD.
         */
        @Min(value = 0, message = "maxRetries must be >= 0")
        @Max(value = 10, message = "maxRetries must be <= 10")
        Integer maxRetries,

        /**
         * Optional idempotency key. Duplicate submissions with the same key
         * are rejected to prevent double-execution.
         */
        @Size(max = 255)
        String idempotencyKey,

        /**
         * Job execution timeout in milliseconds. Defaults to 30,000 (30s).
         */
        Long timeoutMs
) {
    // Defaults applied in the service layer
    public int effectivePriority()   { return priority   != null ? priority   : 3; }
    public int effectiveMaxRetries() { return maxRetries != null ? maxRetries : 3; }
    public long effectiveTimeout()   { return timeoutMs  != null ? timeoutMs  : 30_000L; }
}
