package com.jobqueue.submission.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Guards against duplicate job submissions using an in-memory TTL cache.
 * In production this would be backed by Redis with atomic SET NX EX.
 */
@Component
public class IdempotencyGuard {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyGuard.class);
    private static final long TTL_HOURS = 24;

    // key → jobId
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleaner =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "idempotency-cleaner");
                t.setDaemon(true);
                return t;
            });

    /**
     * Checks if this idempotency key has been processed before.
     * @throws DuplicateJobException if key already exists in the cache.
     */
    public void checkAndRegister(String idempotencyKey) {
        if (idempotencyKey == null) return;
        if (cache.containsKey(idempotencyKey)) {
            log.warn("[IdempotencyGuard] Duplicate key detected: {}", idempotencyKey);
            throw new DuplicateJobException(
                    "A job with idempotency key '" + idempotencyKey + "' has already been submitted.");
        }
        // Register key
        cache.put(idempotencyKey, "registered");
        // Schedule TTL expiry
        cleaner.schedule(() -> {
            cache.remove(idempotencyKey);
            log.debug("[IdempotencyGuard] Expired key: {}", idempotencyKey);
        }, TTL_HOURS, TimeUnit.HOURS);
    }

    /** Legacy API — kept for backward compatibility */
    public boolean isDuplicate(String idempotencyKey, String clientId) {
        if (idempotencyKey == null) return false;
        return cache.containsKey(clientId + ":" + idempotencyKey);
    }

    public void store(String idempotencyKey, String clientId, String jobId) {
        if (idempotencyKey == null) return;
        String key = clientId + ":" + idempotencyKey;
        cache.put(key, jobId);
        cleaner.schedule(() -> cache.remove(key, jobId), TTL_HOURS, TimeUnit.HOURS);
    }

    public String getExistingJobId(String idempotencyKey, String clientId) {
        if (idempotencyKey == null) return null;
        return cache.get(clientId + ":" + idempotencyKey);
    }
}
