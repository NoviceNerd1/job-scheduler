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
    private final org.springframework.mail.javamail.JavaMailSender mailSender;

    public HandlerRegistry(@org.springframework.beans.factory.annotation.Autowired(required = false) org.springframework.mail.javamail.JavaMailSender mailSender) {
        this.mailSender = mailSender;
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
        
        if (mailSender != null) {
            try {
                // Parse basic JSON payload. Example: {"to": "a@b.com", "subject": "Hi", "body": "Hello"}
                String payload = job.getPayload();
                String to = extractJsonValue(payload, "to");
                String subject = extractJsonValue(payload, "subject");
                String body = extractJsonValue(payload, "body");

                if (to != null) {
                    org.springframework.mail.SimpleMailMessage message = new org.springframework.mail.SimpleMailMessage();
                    message.setTo(to);
                    message.setSubject(subject != null ? subject : "Job Scheduler Notification");
                    message.setText(body != null ? body : "Your job ran successfully.");
                    mailSender.send(message);
                    log.info("[Handler:email.send] Real email sent to {} for job {}", to, job.getJobId());
                    return;
                }
            } catch (Exception e) {
                log.error("[Handler:email.send] Failed to send real email for job {}: {}", job.getJobId(), e.getMessage());
                throw new RuntimeException("Email sending failed", e);
            }
        } else {
            log.warn("[Handler:email.send] JavaMailSender not configured. Simulating email send.");
        }

        // Simulate async email call if mailSender is null or parsing failed
        simulateWork(200);
        log.info("[Handler:email.send] Email simulated for job {}", job.getJobId());
    }

    private String extractJsonValue(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        return end == -1 ? null : json.substring(start, end);
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
