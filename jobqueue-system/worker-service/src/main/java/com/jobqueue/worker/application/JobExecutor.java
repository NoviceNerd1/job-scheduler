package com.jobqueue.worker.application;

import com.jobqueue.shared.model.Job;
import com.jobqueue.shared.model.JobStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Executes a single job by delegating to the appropriate HandlerRegistry handler.
 * Tracks in-flight job count for graceful shutdown coordination.
 */
@Component
public class JobExecutor {

    private static final Logger log = LoggerFactory.getLogger(JobExecutor.class);

    private final JdbcClient jdbcClient;
    private final HandlerRegistry handlerRegistry;
    private final RetryHandler retryHandler;
    private final MetricsService metricsService;

    // Tracks active executions for graceful shutdown
    private static final AtomicInteger activeCount = new AtomicInteger(0);
    private final Map<String, String> activeJobs = new ConcurrentHashMap<>();

    public JobExecutor(JdbcClient jdbcClient,
                       HandlerRegistry handlerRegistry,
                       RetryHandler retryHandler,
                       MetricsService metricsService) {
        this.jdbcClient = jdbcClient;
        this.handlerRegistry = handlerRegistry;
        this.retryHandler = retryHandler;
        this.metricsService = metricsService;
    }

    public void execute(String jobId, String workerId) {
        activeCount.incrementAndGet();
        activeJobs.put(jobId, workerId);
        long startMs = System.currentTimeMillis();

        try {
            Optional<Job> jobOpt = findJobById(jobId);
            if (jobOpt.isEmpty()) {
                log.warn("[JobExecutor] Job not found in DB: {}", jobId);
                return;
            }

            Job job = jobOpt.get();

            // Guard against re-execution of non-PENDING/non-RETRY jobs
            if (job.getStatus() != JobStatus.PENDING && job.getStatus() != JobStatus.RETRY
                    && job.getStatus() != JobStatus.LEASED) {
                log.warn("[JobExecutor] Skipping job {} — unexpected status: {}", jobId, job.getStatus());
                return;
            }

            // Mark RUNNING
            updateStatus(jobId, JobStatus.RUNNING, null);
            log.info("[JobExecutor] Executing job {} type={} attempt={}/{}",
                    jobId, job.getType(), job.getAttempts() + 1, job.getMaxRetries());

            // Delegate to handler
            handlerRegistry.execute(job);

            // Mark COMPLETED
            updateStatus(jobId, JobStatus.COMPLETED, null);
            long duration = System.currentTimeMillis() - startMs;
            metricsService.recordJobCompletion(job.getType(), "success", duration);
            log.info("[JobExecutor] Job {} completed in {}ms", jobId, duration);

        } catch (Exception e) {
            log.error("[JobExecutor] Job {} failed: {}", jobId, e.getMessage(), e);
            handleJobFailure(jobId, e.getMessage());
            long duration = System.currentTimeMillis() - startMs;
            // Best effort jobType resolution for metrics
            String jobType = "unknown";
            try {
                Optional<Job> failedJob = findJobById(jobId);
                if (failedJob.isPresent()) jobType = failedJob.get().getType();
            } catch (Exception ignored) {}
            metricsService.recordJobCompletion(jobType, "failure", duration);
        } finally {
            activeCount.decrementAndGet();
            activeJobs.remove(jobId);
        }
    }

    private void handleJobFailure(String jobId, String error) {
        Optional<Job> jobOpt = findJobById(jobId);
        if (jobOpt.isEmpty()) return;
        Job job = jobOpt.get();
        retryHandler.handleFailure(jobId, job.getAttempts(), job.getMaxRetries(), error);
    }

    private void updateStatus(String jobId, JobStatus status, String error) {
        if (status == JobStatus.COMPLETED) {
            jdbcClient.sql("""
                    UPDATE jobs
                    SET status = ?, updated_at = ?, completed_at = ?, last_error = ?
                    WHERE job_id = ?
                    """)
                    .param(status.name())
                    .param(Timestamp.from(Instant.now()))
                    .param(Timestamp.from(Instant.now()))
                    .param(error)
                    .param(jobId)
                    .update();
        } else {
            jdbcClient.sql("""
                    UPDATE jobs SET status = ?, updated_at = ?, last_error = ?
                    WHERE job_id = ?
                    """)
                    .param(status.name())
                    .param(Timestamp.from(Instant.now()))
                    .param(error)
                    .param(jobId)
                    .update();
        }
    }

    private Optional<Job> findJobById(String jobId) {
        return jdbcClient.sql("SELECT * FROM jobs WHERE job_id = ?")
                .param(jobId)
                .query(this::mapRow)
                .optional();
    }

    private Job mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return Job.builder()
                .jobId(rs.getString("job_id"))
                .type(rs.getString("job_type"))
                .payload(rs.getString("payload"))
                .priority(com.jobqueue.shared.model.Priority.fromValue(rs.getInt("priority")))
                .status(JobStatus.valueOf(rs.getString("status")))
                .attempts(rs.getInt("attempts"))
                .maxRetries(rs.getInt("max_retries"))
                .nextRetryAt(rs.getTimestamp("next_retry_at") != null
                        ? rs.getTimestamp("next_retry_at").toInstant() : null)
                .timeoutMs(rs.getLong("timeout_ms"))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .updatedAt(rs.getTimestamp("updated_at").toInstant())
                .completedAt(rs.getTimestamp("completed_at") != null
                        ? rs.getTimestamp("completed_at").toInstant() : null)
                .lastError(rs.getString("last_error"))
                .idempotencyKey(rs.getString("idempotency_key"))
                .build();
    }

    public static int getActiveCount() {
        return activeCount.get();
    }
}
