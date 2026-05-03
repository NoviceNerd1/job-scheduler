package com.jobqueue.worker.infrastructure.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Redis queue client for the worker-service.
 * Uses the same Lua scripts as the submission-service to atomically pop/renew leases.
 */
@Component
public class WorkerRedisClient {

    private static final Logger log = LoggerFactory.getLogger(WorkerRedisClient.class);
    private static final String LEASE_KEY = "job:leases";

    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<List> popScript;
    private final DefaultRedisScript<Long> renewScript;

    public WorkerRedisClient(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;

        this.popScript = new DefaultRedisScript<>();
        popScript.setLocation(new ClassPathResource("lua/pop_job.lua"));
        popScript.setResultType(List.class);

        this.renewScript = new DefaultRedisScript<>();
        renewScript.setLocation(new ClassPathResource("lua/renew_lease.lua"));
        renewScript.setResultType(Long.class);
    }

    /**
     * Atomically pop the highest-priority available job and acquire a lease.
     * Tries priority queues 1 → 5 (1 = highest).
     */
    public String pop(String workerId, long leaseTtlMs) {
        for (int priority = 1; priority <= 5; priority++) {
            String queueKey = "queue:priority:" + priority;
            try {
                List<?> result = redisTemplate.execute(
                        popScript,
                        List.of(queueKey, LEASE_KEY),
                        workerId, String.valueOf(leaseTtlMs)
                );
                if (result != null && !result.isEmpty() && result.get(0) != null) {
                    String jobId = result.get(0).toString();
                    log.debug("[WorkerRedisClient] Popped job {} from priority:{}", jobId, priority);
                    return jobId;
                }
            } catch (Exception e) {
                log.error("[WorkerRedisClient] Error popping from queue priority:{} — {}", priority, e.getMessage());
            }
        }
        return null;
    }

    /**
     * Renew the lease for a job that is still executing.
     * Returns true if the lease is still held by this worker.
     */
    public boolean renewLease(String jobId, String workerId, long ttlMs) {
        try {
            Long result = redisTemplate.execute(
                    renewScript,
                    List.of(LEASE_KEY),
                    jobId, workerId, String.valueOf(ttlMs)
            );
            return result != null && result == 1L;
        } catch (Exception e) {
            log.error("[WorkerRedisClient] Lease renewal error for job {}: {}", jobId, e.getMessage());
            return false;
        }
    }

    /**
     * Release the lease once the job completes or fails.
     */
    public void releaseLease(String jobId) {
        redisTemplate.opsForHash().delete(LEASE_KEY, jobId);
        log.debug("[WorkerRedisClient] Released lease for job {}", jobId);
    }

    /**
     * Report worker heartbeat to Redis with 30-second TTL.
     */
    public void heartbeat(String workerId) {
        redisTemplate.opsForValue().set(
                "worker:heartbeat:" + workerId,
                Instant.now().toString(),
                Duration.ofSeconds(30)
        );
    }

    /**
     * Return current queue depth for a priority band.
     */
    public long queueDepth(int priority) {
        Long size = redisTemplate.opsForZSet().size("queue:priority:" + priority);
        return size != null ? size : 0L;
    }
}
