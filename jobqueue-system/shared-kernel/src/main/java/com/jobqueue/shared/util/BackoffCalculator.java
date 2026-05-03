package com.jobqueue.shared.util;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

public class BackoffCalculator {
    
    private static final long BASE_DELAY_MS = 100;
    private static final long MAX_DELAY_MS = 60000;
    
    public static Instant calculateNextRetry(int attempt, Instant originalFailureTime) {
        if (attempt <= 0) {
            return Instant.now().plusMillis(BASE_DELAY_MS);
        }
        
        long exponentialDelay = BASE_DELAY_MS * (long) Math.pow(2, attempt - 1);
        long cappedDelay = Math.min(exponentialDelay, MAX_DELAY_MS);
        
        // Full jitter: random between 0 and cappedDelay
        long jitteredDelay = ThreadLocalRandom.current().nextLong(cappedDelay);
        
        return originalFailureTime.plusMillis(jitteredDelay);
    }
    
    // Exponential backoff with jitter for retry after specific attempt numbers
    public static long getDelayForAttempt(int attempt) {
        if (attempt == 1) return 0;
        if (attempt == 2) return 100;
        if (attempt == 3) return 200;
        if (attempt == 4) return 500;
        if (attempt == 5) return 1000;
        if (attempt == 6) return 2000;
        if (attempt == 7) return 5000;
        if (attempt == 8) return 10000;
        if (attempt == 9) return 30000;
        return 60000;
    }
}
