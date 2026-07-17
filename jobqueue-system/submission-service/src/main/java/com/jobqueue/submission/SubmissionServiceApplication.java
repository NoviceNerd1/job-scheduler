package com.jobqueue.submission;

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
public class SubmissionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SubmissionServiceApplication.class, args);
    }

    @GetMapping("/")
    public Map<String, Object> home() {
        return Map.of(
                "service",   "submission-service",
                "status",    "running",
                "port",      8081,
                "api",       "http://localhost:8081/api/v1/jobs",
                "swagger",   "http://localhost:8081/swagger-ui.html",
                "health",    "http://localhost:8081/actuator/health",
                "timestamp", Instant.now().toString()
        );
    }
}