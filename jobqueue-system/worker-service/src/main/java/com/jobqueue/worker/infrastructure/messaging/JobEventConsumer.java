package com.jobqueue.worker.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes job lifecycle events from Kafka topic "job.events".
 * Used for updating metrics, triggering webhooks, and audit logging.
 */
@Component
public class JobEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(JobEventConsumer.class);

    @KafkaListener(topics = "job.events", groupId = "worker-group")
    public void consume(String event) {
        log.info("[JobEventConsumer] Received event: {}", event);
        try {
            String eventType = extractEventType(event);
            switch (eventType) {
                case "job.completed" -> handleJobCompleted(event);
                case "job.failed"    -> handleJobFailed(event);
                case "job.submitted" -> handleJobSubmitted(event);
                default -> log.warn("[JobEventConsumer] Unknown event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("[JobEventConsumer] Error processing event: {}", e.getMessage(), e);
        }
    }

    private void handleJobCompleted(String event) {
        log.info("[JobEventConsumer] Processing job.completed: {}", event);
        // Note: Downstream actions (webhook delivery, analytics updates) go here
    }

    private void handleJobFailed(String event) {
        log.warn("[JobEventConsumer] Processing job.failed: {}", event);
        // Note: Alerting and error metric updates go here
    }

    private void handleJobSubmitted(String event) {
        log.debug("[JobEventConsumer] Processing job.submitted: {}", event);
        // Note: Pre-fetching configs or cache warming go here
    }

    private String extractEventType(String event) {
        // Simple JSON field extraction without Jackson dependency on this class
        if (event == null) return "unknown";
        int idx = event.indexOf("\"eventType\"");
        if (idx < 0) idx = event.indexOf("\"event_type\"");
        if (idx < 0) return "unknown";
        int colon = event.indexOf(':', idx);
        int start = event.indexOf('"', colon + 1) + 1;
        int end   = event.indexOf('"', start);
        if (start < 1 || end < 0) return "unknown";
        return event.substring(start, end);
    }
}
