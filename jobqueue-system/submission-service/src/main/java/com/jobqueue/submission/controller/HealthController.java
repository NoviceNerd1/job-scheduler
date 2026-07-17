package com.jobqueue.submission.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class HealthController {
    
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
            "status", "UP",
            "service", "submission-service",
            "version", "1.0.0"
        );
    }
    
    @GetMapping("/ready")
    public Map<String, String> ready() {
        // Will check DB and Redis connectivity later
        return Map.of("status", "READY");
    }
}
