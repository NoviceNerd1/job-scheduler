package com.jobqueue.submission.controller;

import com.jobqueue.shared.model.Job;
import com.jobqueue.submission.domain.JobSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * REST controller for job submission and status queries.
 * All endpoints are under /api/v1/jobs.
 */
@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "Jobs", description = "Submit, query and manage distributed jobs")
public class JobController {

    private static final Logger log = LoggerFactory.getLogger(JobController.class);

    private final JobSubmissionService submissionService;

    public JobController(JobSubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    /**
     * Submit a new job for async execution.
     * Returns 202 Accepted with the job ID and metadata.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Submit a job", description = "Enqueues a job for distributed async execution")
    public ResponseEntity<JobSubmitResponse> submit(@Valid @RequestBody JobSubmitRequest request) {
        log.info("[JobController] Received job submission: type={} priority={}",
                request.type(), request.effectivePriority());

        Job job = submissionService.submit(request);

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(new JobSubmitResponse(
                        job.getJobId(),
                        job.getType(),
                        job.getStatus().name(),
                        job.getPriority().getValue(),
                        Instant.now(),
                        "Job accepted and enqueued"
                ));
    }

    /**
     * Get the current status of a job by its ID.
     */
    @GetMapping("/{jobId}")
    @Operation(summary = "Get job status", description = "Returns current status and metadata for a job")
    public ResponseEntity<JobStatusResponse> getStatus(@PathVariable String jobId) {
        log.debug("[JobController] Status requested for job {}", jobId);
        return ResponseEntity.ok(submissionService.getStatus(jobId));
    }

    /**
     * Simple ping endpoint — useful for smoke-testing after deploy.
     */
    @GetMapping("/ping")
    @Operation(summary = "Ping", description = "Returns pong — use to verify the API is reachable")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("pong");
    }
}
