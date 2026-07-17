package com.jobqueue.worker.application;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Centralises all Micrometer metric emissions for the worker service.
 * Metrics are auto-scraped by Prometheus via /actuator/prometheus.
 */
@Component
public class MetricsService {

    private final MeterRegistry meterRegistry;

    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Increment job completion counter and record execution duration.
     * @param jobType   the job type string (e.g. "email.send")
     * @param outcome   "success" | "failure"
     * @param durationMs execution wall-clock time in ms
     */
    public void recordJobCompletion(String jobType, String outcome, long durationMs) {
        meterRegistry.counter("job.completions",
                "job_type", jobType,
                "outcome", outcome
        ).increment();

        meterRegistry.timer("job.execution.duration",
                "job_type", jobType
        ).record(Duration.ofMillis(durationMs));
    }

    /**
     * Record current queue depth for a given priority band.
     * Called periodically from the QueuePoller heartbeat.
     */
    public void recordQueueDepth(int priority, long depth) {
        meterRegistry.gauge("queue.depth",
                Tags.of("priority", String.valueOf(priority)),
                depth);
    }

    /**
     * Record number of currently active (in-flight) jobs.
     */
    public void recordActiveJobs(int count) {
        meterRegistry.gauge("worker.active_jobs", count);
    }

    /**
     * Increment lease renewal counter — useful for detecting runaway jobs.
     */
    public void recordLeaseRenewal(boolean success) {
        meterRegistry.counter("worker.lease.renewals",
                "result", success ? "ok" : "stolen"
        ).increment();
    }
}
