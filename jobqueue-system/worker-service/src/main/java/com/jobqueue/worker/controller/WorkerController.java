package com.jobqueue.worker.controller;

import com.jobqueue.worker.application.JobExecutor;
import com.jobqueue.worker.infrastructure.queue.WorkerRedisClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * REST controller for the worker-service.
 * Exposes runtime status, queue depth, and active job counts.
 */
@RestController
@RequestMapping("/api/v1/worker")
@Tag(name = "Worker", description = "Worker service runtime status and diagnostics")
public class WorkerController {

    private final WorkerRedisClient redisClient;

    public WorkerController(WorkerRedisClient redisClient) {
        this.redisClient = redisClient;
    }

    /**
     * Returns current queue depth per priority and active job count.
     */
    @GetMapping("/status")
    @Operation(summary = "Worker status", description = "Current queue depths and active job count")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> body = Map.of(
                "service",    "worker-service",
                "timestamp",  Instant.now().toString(),
                "activeJobs", JobExecutor.getActiveCount(),
                "queueDepth", Map.of(
                        "priority1", redisClient.queueDepth(1),
                        "priority2", redisClient.queueDepth(2),
                        "priority3", redisClient.queueDepth(3),
                        "priority4", redisClient.queueDepth(4),
                        "priority5", redisClient.queueDepth(5)
                )
        );
        return ResponseEntity.ok(body);
    }

    /**
     * Simple ping — verifies the service is up.
     */
    @GetMapping("/ping")
    @Operation(summary = "Ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("pong");
    }
}
