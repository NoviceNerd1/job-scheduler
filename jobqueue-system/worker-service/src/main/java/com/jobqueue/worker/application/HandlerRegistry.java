package com.jobqueue.worker.application;

import com.jobqueue.shared.model.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Registry that maps job types to their handler functions.
 * Provides a plug-in mechanism for adding new job handlers without
 * modifying the executor or poller.
 */
@Component
public class HandlerRegistry {

    private static final Logger log = LoggerFactory.getLogger(HandlerRegistry.class);

    private final Map<String, Consumer<Job>> handlers = new ConcurrentHashMap<>();

    public HandlerRegistry() {
        // Register built-in handlers
        register("email.send", this::handleEmailSend);
        register("report.generate", this::handleReportGenerate);
        register("notification.push", this::handlePushNotification);
        register("data.export", this::handleDataExport);
        register("test", this::handleTest);
    }

    /**
     * Register a handler for a given job type.
     */
    public void register(String jobType, Consumer<Job> handler) {
        handlers.put(jobType, handler);
        log.info("[HandlerRegistry] Registered handler for job type: {}", jobType);
    }

    /**
     * Execute the job using the registered handler, or fall back to default.
     */
    public void execute(Job job) {
        Consumer<Job> handler = handlers.get(job.getType());
        if (handler == null) {
            log.warn("[HandlerRegistry] No handler for job type '{}' — using default", job.getType());
            handleDefault(job);
            return;
        }
        log.debug("[HandlerRegistry] Dispatching job {} to handler '{}'", job.getJobId(), job.getType());
        handler.accept(job);
    }

    public boolean hasHandler(String jobType) {
        return handlers.containsKey(jobType);
    }

    // ── Built-in handlers ────────────────────────────────────────────────────

    private void handleEmailSend(Job job) {
        log.info("[Handler:email.send] Sending email for job {} payload={}", job.getJobId(), job.getPayload());
        // Simulate async email call
        simulateWork(200);
        log.info("[Handler:email.send] Email sent for job {}", job.getJobId());
    }

    private void handleReportGenerate(Job job) {
        log.info("[Handler:report.generate] Generating report for job {}", job.getJobId());
        simulateWork(500);
        log.info("[Handler:report.generate] Report generated for job {}", job.getJobId());
    }

    private void handlePushNotification(Job job) {
        log.info("[Handler:notification.push] Pushing notification for job {}", job.getJobId());
        simulateWork(100);
        log.info("[Handler:notification.push] Notification sent for job {}", job.getJobId());
    }

    private void handleDataExport(Job job) {
        log.info("[Handler:data.export] Starting data export for job {}", job.getJobId());
        simulateWork(1000);
        log.info("[Handler:data.export] Data export complete for job {}", job.getJobId());
    }

    private void handleTest(Job job) {
        log.info("[Handler:test] Test job {} executed successfully", job.getJobId());
        simulateWork(50);
    }

    private void handleDefault(Job job) {
        log.warn("[Handler:default] Executing job {} with unknown type '{}' — no-op",
                job.getJobId(), job.getType());
    }

    private void simulateWork(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
