package com.jobqueue.submission.infrastructure.queue;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.core.io.ClassPathResource;
import java.time.Instant;
import java.util.List;

@Component
public class RedisQueueClient {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<String> popScript;
    private final DefaultRedisScript<Long> renewScript;
    
    public RedisQueueClient(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        
        this.popScript = new DefaultRedisScript<>();
        popScript.setLocation(new ClassPathResource("lua/pop_job.lua"));
        popScript.setResultType(String.class);
        
        this.renewScript = new DefaultRedisScript<>();
        renewScript.setLocation(new ClassPathResource("lua/renew_lease.lua"));
        renewScript.setResultType(Long.class);
    }
    
    public void enqueue(String jobId, int priority) {
        String queueKey = "queue:priority:" + priority;
        double score = Instant.now().toEpochMilli();
        redisTemplate.opsForZSet().add(queueKey, jobId, score);
    }
    
    public String pop(String workerId, long leaseTtlMs) {
        String leaseKey = "job:leases";
        
        // Try each priority from 1 (highest) to 5
        for (int priority = 1; priority <= 5; priority++) {
            String queueKey = "queue:priority:" + priority;
            String jobId = redisTemplate.execute(
                popScript,
                List.of(queueKey, leaseKey),
                workerId, String.valueOf(leaseTtlMs)
            );
            if (jobId != null) return jobId;
        }
        return null;
    }
    
    public boolean renewLease(String jobId, String workerId, long ttlMs) {
        String leaseKey = "job:leases";
        Long result = redisTemplate.execute(
            renewScript,
            List.of(leaseKey),
            jobId, workerId, String.valueOf(ttlMs)
        );
        return result != null && result == 1;
    }
    
    public void complete(String jobId, String workerId) {
        String leaseKey = "job:leases";
        redisTemplate.opsForHash().delete(leaseKey, jobId);
        // Also remove from processing set if you have one
    }
}
