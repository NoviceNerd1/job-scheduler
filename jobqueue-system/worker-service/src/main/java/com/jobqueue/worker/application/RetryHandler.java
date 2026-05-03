package com.jobqueue.worker.application;

import com.jobqueue.shared.util.BackoffCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Handles job failure by either scheduling a retry (with exponential backoff + full jitter)
 * or moving the job to DEAD status after max retries are exhausted.
 */
@Component
public class RetryHandler {

    private static final Logger log = LoggerFactory.getLogger(RetryHandler.class);

    private final JdbcClient jdbcClient;

    public RetryHandler(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void handleFailure(String jobId, int currentAttempts, int maxRetries, String error) {
        int nextAttempt = currentAttempts + 1;

        if (nextAttempt >= maxRetries) {
            log.warn("[RetryHandler] Job {} exceeded max retries ({}), moving to DEAD", jobId, maxRetries);
            moveToDeadLetter(jobId, error, nextAttempt);
        } else {
            Instant nextRetry = BackoffCalculator.calculateNextRetry(nextAttempt, Instant.now());
            log.info("[RetryHandler] Job {} failed (attempt {}/{}), retrying at {}",
                    jobId, nextAttempt, maxRetries, nextRetry);
            scheduleRetry(jobId, nextRetry, nextAttempt, error);
        }
    }

    private void scheduleRetry(String jobId, Instant nextRetry, int nextAttempt, String error) {
        jdbcClient.sql("""
                UPDATE jobs
                SET status = 'RETRY',
                    next_retry_at = ?,
                    attempts = ?,
                    last_error = ?,
                    updated_at = ?
                WHERE job_id = ?
                """)
                .param(Timestamp.from(nextRetry))
                .param(nextAttempt)
                .param(error)
                .param(Timestamp.from(Instant.now()))
                .param(jobId)
                .update();
    }

    private void moveToDeadLetter(String jobId, String error, int finalAttempt) {
        jdbcClient.sql("""
                UPDATE jobs
                SET status = 'DEAD',
                    attempts = ?,
                    last_error = ?,
                    updated_at = ?
                WHERE job_id = ?
                """)
                .param(finalAttempt)
                .param(error)
                .param(Timestamp.from(Instant.now()))
                .param(jobId)
                .update();

        log.error("[RetryHandler] Job {} is now DEAD after {} attempts. Last error: {}",
                jobId, finalAttempt, error);
    }
}
