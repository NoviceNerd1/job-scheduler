package com.jobqueue.worker.config;

import com.jobqueue.worker.application.JobExecutor;
import com.jobqueue.worker.application.QueuePoller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Implements graceful shutdown for the worker service.
 * On Spring context close, signals the poller to stop acquiring new leases,
 * then waits (up to 30s) for in-flight jobs to complete.
 */
@Component
public class GracefulShutdown implements ApplicationListener<ContextClosedEvent> {

    private static final Logger log = LoggerFactory.getLogger(GracefulShutdown.class);
    private static final long MAX_WAIT_MS = 30_000L;

    @Override
    public void onApplicationEvent(@NonNull ContextClosedEvent event) {
        log.info("[GracefulShutdown] Shutdown signal received — stopping lease acquisition");
        QueuePoller.setRunning(false);

        long start = System.currentTimeMillis();
        while (JobExecutor.getActiveCount() > 0) {
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed >= MAX_WAIT_MS) {
                log.warn("[GracefulShutdown] Timeout reached with {} jobs still active — forcing shutdown",
                        JobExecutor.getActiveCount());
                break;
            }
            log.info("[GracefulShutdown] Waiting for {} active jobs to finish... ({}/{}ms)",
                    JobExecutor.getActiveCount(), elapsed, MAX_WAIT_MS);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.info("[GracefulShutdown] Worker shutdown complete. Active jobs remaining: {}",
                JobExecutor.getActiveCount());
    }
}
