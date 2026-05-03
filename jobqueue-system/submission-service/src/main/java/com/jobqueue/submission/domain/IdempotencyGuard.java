package com.jobqueue.submission.domain;

import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class IdempotencyGuard {
    
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
    
    // In-memory for local dev, will be replaced with Redis later
    public boolean isDuplicate(String idempotencyKey, String clientId) {
        if (idempotencyKey == null) return false;
        
        String key = clientId + ":" + idempotencyKey;
        return cache.containsKey(key);
    }
    
    public void store(String idempotencyKey, String clientId, String jobId) {
        if (idempotencyKey == null) return;
        
        String key = clientId + ":" + idempotencyKey;
        cache.put(key, jobId);
        
        // Schedule cleanup (simplified)
        new Thread(() -> {
            try {
                TimeUnit.HOURS.sleep(24);
                cache.remove(key, jobId);
            } catch (InterruptedException ignored) {}
        }).start();
    }
    
    public String getExistingJobId(String idempotencyKey, String clientId) {
        if (idempotencyKey == null) return null;
        
        String key = clientId + ":" + idempotencyKey;
        return cache.get(key);
    }
}
