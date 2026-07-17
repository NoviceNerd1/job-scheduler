package com.jobqueue.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@SpringBootApplication
@EnableScheduling
@RestController
public class WorkerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkerServiceApplication.class, args);
    }

    @GetMapping("/")
    public Map<String, Object> home() {
        return Map.of(
                "service",   "worker-service",
                "status",    "running",
                "port",      8082,
                "api",       "http://localhost:8082/api/v1/worker/status",
                "swagger",   "http://localhost:8082/swagger-ui.html",
                "health",    "http://localhost:8082/actuator/health",
                "timestamp", Instant.now().toString()
        );
    }
}