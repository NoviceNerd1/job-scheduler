package com.jobqueue.submission.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI / Swagger UI configuration.
 * UI is available at: http://localhost:8081/swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI jobQueueOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Distributed Job Scheduler — Submission Service API")
                        .description("""
                                REST API for submitting and tracking distributed async jobs.
                                
                                Key features:
                                - Priority-based job queuing (1=highest, 5=lowest)
                                - Idempotent submission via idempotency key
                                - Exponential backoff retry with full jitter
                                - Real-time job status tracking
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Job Scheduler Team")
                                .email("dev@jobqueue.io"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8081").description("Local development"),
                        new Server().url("http://localhost:8082").description("Worker service")
                ));
    }
}
