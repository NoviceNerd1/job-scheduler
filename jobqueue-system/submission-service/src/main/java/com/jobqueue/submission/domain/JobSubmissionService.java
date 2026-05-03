package com.jobqueue.submission.domain;

import com.jobqueue.shared.model.Job;
import com.jobqueue.shared.model.Priority;
import com.jobqueue.submission.controller.JobSubmitRequest;
import com.jobqueue.submission.controller.JobStatusResponse;
import com.jobqueue.submission.infrastructure.persistence.JobRepository;
import com.jobqueue.submission.infrastructure.queue.RedisQueueClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Application-layer service for job submission.
 * Applies idempotency guard, persists to PostgreSQL, and enqueues into Redis.
 */
@Service
public class JobSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(JobSubmissionService.class);

    private final JobRepository jobRepository;
    private final RedisQueueClient redisQueueClient;
    private final IdempotencyGuard idempotencyGuard;

    public JobSubmissionService(JobRepository jobRepository,
                                RedisQueueClient redisQueueClient,
                                IdempotencyGuard idempotencyGuard) {
        this.jobRepository = jobRepository;
        this.redisQueueClient = redisQueueClient;
        this.idempotencyGuard = idempotencyGuard;
    }

    /**
     * Submit a new job. Idempotency key prevents duplicate submissions.
     * @return the persisted Job entity
     * @throws DuplicateJobException if the idempotency key has already been used
     */
    public Job submit(JobSubmitRequest request) {
        String idempotencyKey = request.idempotencyKey() != null
                ? request.idempotencyKey()
                : UUID.randomUUID().toString();

        // Idempotency check — throws if key already processed
        idempotencyGuard.checkAndRegister(idempotencyKey);

        // Convert payload map to JSON string
        String payloadJson = payloadToJson(request.payload());

        Job job = Job.create(
                request.type(),
                payloadJson,
                Priority.fromValue(request.effectivePriority()),
                request.effectiveMaxRetries(),
                idempotencyKey,
                request.effectiveTimeout()
        );

        // 1. Persist to PostgreSQL (source of truth)
        jobRepository.save(job);
        log.info("[JobSubmissionService] Persisted job {} type={} priority={}",
                job.getJobId(), job.getType(), job.getPriority());

        // 2. Enqueue into Redis priority queue
        redisQueueClient.enqueue(job.getJobId(), job.getPriority().getValue());
        log.info("[JobSubmissionService] Enqueued job {} into Redis priority:{}",
                job.getJobId(), job.getPriority().getValue());

        return job;
    }

    /**
     * Look up job status from PostgreSQL.
     */
    public JobStatusResponse getStatus(String jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException("Job not found: " + jobId));

        return new JobStatusResponse(
                job.getJobId(),
                job.getType(),
                job.getStatus().name(),
                job.getPriority().getValue(),
                job.getAttempts(),
                job.getMaxRetries(),
                job.getLastError(),
                job.getNextRetryAt(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getCompletedAt()
        );
    }

    private String payloadToJson(java.util.Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) return "{}";
        try {
            // Simple approach — in production, inject ObjectMapper
            var sb = new StringBuilder("{");
            payload.forEach((k, v) -> {
                sb.append("\"").append(k).append("\":");
                if (v instanceof String) sb.append("\"").append(v).append("\"");
                else sb.append(v);
                sb.append(",");
            });
            if (sb.charAt(sb.length() - 1) == ',') sb.deleteCharAt(sb.length() - 1);
            sb.append("}");
            return sb.toString();
        } catch (Exception e) {
            return "{}";
        }
    }
}
