package com.jobqueue.submission.infrastructure.persistence;

import com.jobqueue.shared.model.Job;
import com.jobqueue.shared.model.JobStatus;
import com.jobqueue.shared.model.Priority;
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
            .param(job.getCreatedAt() != null ? java.sql.Timestamp.from(job.getCreatedAt()) : null)
            .param(job.getUpdatedAt() != null ? java.sql.Timestamp.from(job.getUpdatedAt()) : null)
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
