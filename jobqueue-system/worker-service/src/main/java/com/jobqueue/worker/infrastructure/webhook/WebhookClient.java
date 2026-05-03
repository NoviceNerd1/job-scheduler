package com.jobqueue.worker.infrastructure.webhook;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

/**
 * Sends HTTP webhook callbacks when a job completes or fails.
 * Protected by a Resilience4j circuit breaker named "webhook".
 * If the downstream URL is flaky, the circuit opens and calls are short-circuited.
 */
@Component
public class WebhookClient {

    private static final Logger log = LoggerFactory.getLogger(WebhookClient.class);

    private final RestClient restClient;

    public WebhookClient() {
        this.restClient = RestClient.builder()
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("X-JobQueue-Source", "job-scheduler-v1")
                .build();
    }

    /**
     * Deliver a webhook to the given URL with job completion metadata.
     * Protected by the "webhook" circuit breaker.
     */
    @CircuitBreaker(name = "webhook", fallbackMethod = "fallback")
    public void sendWebhook(String url, String jobId, String eventType, Object result) {
        if (url == null || url.isBlank()) return;

        var payload = Map.of(
                "event",     eventType,
                "jobId",     jobId,
                "result",    result != null ? result : Map.of(),
                "timestamp", Instant.now().toString()
        );

        log.info("[WebhookClient] Sending webhook to {} for job {}", url, jobId);

        @SuppressWarnings("null")
        var entity = restClient.post()
                .uri(url)
                .body(payload)
                .retrieve()
                .toBodilessEntity();

        if (entity.getStatusCode().isError()) {
            log.error("[WebhookClient] Webhook failed for job {} with status {}", jobId, entity.getStatusCode());
        }

        log.info("[WebhookClient] Webhook delivered to {} for job {}", url, jobId);
    }

    /**
     * Fallback invoked when the circuit is open or the call fails.
     * Logs the failure — in production this would store for later retry.
     */
    public void fallback(String url, String jobId, String eventType, Object result, Exception ex) {
        log.error("[WebhookClient] Circuit breaker fallback — Event: {}, Job ID: {}, Result: {}, Target URL: {}, Error: {}",
                eventType, jobId, result, url, ex.getMessage());
        // Note: For production, persist to a webhook_retry table for later re-delivery
    }
}
