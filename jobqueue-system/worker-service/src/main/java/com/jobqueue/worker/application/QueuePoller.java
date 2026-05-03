package com.jobqueue.worker.application;

import com.jobqueue.worker.infrastructure.queue.WorkerRedisClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Polls priority Redis queues every 100ms using virtual threads.
 * On finding a job, submits execution to the virtual-thread executor.
 * Also sends a 10-second heartbeat and records queue depth metrics.
 */
@Component
public class QueuePoller {

    private static final Logger log = LoggerFactory.getLogger(QueuePoller.class);

    private static final long LEASE_TTL_MS = 35_000L;   // 35 seconds
    private static final long HEARTBEAT_TTL_MS = 30_000L;

    private final WorkerRedisClient redisClient;
    private final JobExecutor jobExecutor;
    private final MetricsService metricsService;

    // Thread executor — one daemon thread per job task (Java 17 compatible)
    // Switch to Executors.newVirtualThreadPerTaskExecutor() when upgrading to Java 21
    private final ExecutorService virtualThreadExecutor =
            Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName("worker-exec-" + t.getId());
                return t;
            });

    // Shutdown flag — set to false by GracefulShutdown to stop new lease acquisition
    private static final AtomicBoolean running = new AtomicBoolean(true);

    public QueuePoller(WorkerRedisClient redisClient,
                       JobExecutor jobExecutor,
                       MetricsService metricsService) {
        this.redisClient = redisClient;
        this.jobExecutor = jobExecutor;
        this.metricsService = metricsService;
    }

    /**
     * Poll all priority queues every 100ms.
     * If a job is available, it is dispatched asynchronously via virtual thread.
     */
    @Scheduled(fixedDelay = 100)
    public void poll() {
        if (!running.get()) return;

        String workerId = getWorkerId();
        String jobId = redisClient.pop(workerId, LEASE_TTL_MS);

        if (jobId != null) {
            log.debug("[QueuePoller] Dispatching job {} to executor", jobId);
            final String fJobId = jobId;
            virtualThreadExecutor.submit(() -> {
                try {
                    jobExecutor.execute(fJobId, workerId);
                } catch (Exception e) {
                    log.error("[QueuePoller] Unhandled error executing job {}: {}", fJobId, e.getMessage(), e);
                } finally {
                    redisClient.releaseLease(fJobId);
                }
            });
            metricsService.recordActiveJobs(JobExecutor.getActiveCount());
        }
    }

    /**
     * Send a heartbeat to Redis every 10 seconds so the cluster knows this worker is alive.
     * Also records current queue depth per priority band.
     */
    @Scheduled(fixedDelay = 10_000)
    public void heartbeat() {
        String workerId = getWorkerId();
        redisClient.heartbeat(workerId);
        log.debug("[QueuePoller] Heartbeat sent for worker {}", workerId);

        // Emit queue depth metrics for each priority band
        for (int p = 1; p <= 5; p++) {
            long depth = redisClient.queueDepth(p);
            metricsService.recordQueueDepth(p, depth);
        }
        metricsService.recordActiveJobs(JobExecutor.getActiveCount());
    }

    public static void setRunning(boolean value) {
        running.set(value);
    }

    private String getWorkerId() {
        return System.getenv().getOrDefault("HOSTNAME", "worker-local")
                + "-" + ProcessHandle.current().pid();
    }
}
